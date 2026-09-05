package com.nidus.twinly.people.service;

import com.google.firebase.messaging.Message;
import com.nidus.twinly.common.fcm.FcmSender;
import com.nidus.twinly.common.fcm.FeedPushContent;
import com.nidus.twinly.common.fcm.PushMessage;
import com.nidus.twinly.common.fcm.PushType;
import com.nidus.twinly.common.fcm.PushMessageBuilder;
import com.nidus.twinly.common.fcm.PushRecipientResolver;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.device.domain.DevicePlatform;
import com.nidus.twinly.device.entity.Device;
import com.nidus.twinly.notification.domain.AppNotificationFeedTargetType;
import com.nidus.twinly.notification.domain.AppNotificationFeedType;
import com.nidus.twinly.notification.domain.NotificationType;
import com.nidus.twinly.notification.entity.AppNotificationFeed;
import com.nidus.twinly.notification.repository.AppNotificationFeedRepository;
import com.nidus.twinly.people.repository.TwinViewRepository;
import com.nidus.twinly.people.repository.TwinViewRepository.ViewerCountProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class TwinViewNotificationServiceUnitTest {

    private static final LocalDate SENDING_DAY = LocalDate.of(2026, 8, 25);
    private static final LocalDate NON_SENDING_DAY = LocalDate.of(2026, 8, 24);

    @Mock
    TwinViewRepository twinViewRepository;

    @Mock
    AppNotificationFeedRepository appNotificationFeedRepository;

    @Mock
    PushRecipientResolver pushRecipientResolver;

    @Mock
    PushMessageBuilder pushMessageBuilder;

    @Mock
    FcmSender fcmSender;

    @InjectMocks
    TwinViewNotificationService twinViewNotificationService;

    @Test
    @DisplayName("5일 주기가 아닌 날에는 집계 쿼리조차 실행하지 않는다")
    void skips_entirely_on_non_sending_day() {
        // when: 주기에 해당하지 않는 날
        twinViewNotificationService.notifyViewerCounts(NON_SENDING_DAY);

        // then: 집계도 알림 생성도 없음
        then(twinViewRepository).should(never()).countDistinctViewersByViewedAtRange(any(), any());
        then(appNotificationFeedRepository).should(never()).saveAll(anyList());
        then(fcmSender).should(never()).send(anyList());
    }

    @Test
    @DisplayName("집계 구간은 발송일 20시로 끝나고 5일 전 20시에 시작한다")
    void aggregates_five_day_window_ending_at_eight_pm() {
        // given: 열람 기록이 없는 상태
        given(twinViewRepository.countDistinctViewersByViewedAtRange(any(), any())).willReturn(List.of());

        // when: 발송일에 실행
        twinViewNotificationService.notifyViewerCounts(SENDING_DAY);

        // then: 구간 경계가 KST 20시 기준으로 정확히 5일
        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        then(twinViewRepository).should().countDistinctViewersByViewedAtRange(from.capture(), to.capture());

        assertThat(to.getValue()).isEqualTo(kstEightPm(SENDING_DAY));
        assertThat(from.getValue()).isEqualTo(kstEightPm(LocalDate.of(2026, 8, 20)));
    }

    @Test
    @DisplayName("열람 기록이 없으면 앱 알림도 만들지 않고 발송도 하지 않는다")
    void creates_nothing_when_no_view_recorded() {
        // given: 집계 결과 없음
        given(twinViewRepository.countDistinctViewersByViewedAtRange(any(), any())).willReturn(List.of());

        // when: 발송일에 실행
        twinViewNotificationService.notifyViewerCounts(SENDING_DAY);

        // then: 알림 생성·수신자 조회·발송 모두 없음
        then(appNotificationFeedRepository).should(never()).saveAll(anyList());
        then(pushRecipientResolver).should(never()).resolve(anyList(), any());
        then(fcmSender).should(never()).send(anyList());
    }

    @Test
    @DisplayName("유저마다 자신의 열람자 수를 담은 문구로 앱 알림이 본인 프로필을 대상으로 생성된다")
    void creates_feed_per_user_with_own_viewer_count() {
        // given: 10번 유저는 3명, 20번 유저는 7명이 열람했다
        given(twinViewRepository.countDistinctViewersByViewedAtRange(any(), any()))
                .willReturn(List.of(viewerCount(10L, 3L), viewerCount(20L, 7L)));
        givenSaveAllAssignsIds();
        given(pushRecipientResolver.resolve(anyList(), eq(NotificationType.EVENT))).willReturn(List.of());

        // when: 발송일에 실행
        twinViewNotificationService.notifyViewerCounts(SENDING_DAY);

        // then: 유저별 제목·본문과 타입, 그리고 대상은 본인 프로필
        ArgumentCaptor<List<AppNotificationFeed>> feeds = ArgumentCaptor.captor();
        then(appNotificationFeedRepository).should().saveAll(feeds.capture());

        assertThat(feeds.getValue())
                .extracting(AppNotificationFeed::getUserId, AppNotificationFeed::getTitle,
                        AppNotificationFeed::getBody, AppNotificationFeed::getType,
                        AppNotificationFeed::getTargetKind, AppNotificationFeed::getTargetUserId)
                .containsExactlyInAnyOrder(
                        tuple(10L, "3명이 회원님에게 관심을 보였어요.",
                                "지난 5일간 3명이 회원님의 프로필 또는 대화 기록을 열람했어요.",
                                AppNotificationFeedType.TWIN_VIEW,
                                AppNotificationFeedTargetType.PROFILE, 10L),
                        tuple(20L, "7명이 회원님에게 관심을 보였어요.",
                                "지난 5일간 7명이 회원님의 프로필 또는 대화 기록을 열람했어요.",
                                AppNotificationFeedType.TWIN_VIEW,
                                AppNotificationFeedTargetType.PROFILE, 20L));
    }

    @Test
    @DisplayName("푸시에는 저장된 앱 알림 id가 실려 클라이언트가 읽음 처리할 수 있다")
    void push_carries_saved_feed_id() {
        // given: 열람자가 있고 기기도 등록된 유저
        given(twinViewRepository.countDistinctViewersByViewedAtRange(any(), any()))
                .willReturn(List.of(viewerCount(10L, 3L)));
        givenSaveAllAssignsIds();
        given(pushRecipientResolver.resolve(anyList(), eq(NotificationType.EVENT)))
                .willReturn(List.of(device(10L, "token10")));
        given(pushMessageBuilder.feed(anyList(), any())).willReturn(List.of(pushMessage("token10")));

        // when: 발송일에 실행
        twinViewNotificationService.notifyViewerCounts(SENDING_DAY);

        // then: 저장된 알림의 id·타입·대상이 푸시 콘텐츠에 실린다
        ArgumentCaptor<FeedPushContent> content = ArgumentCaptor.forClass(FeedPushContent.class);
        then(pushMessageBuilder).should().feed(anyList(), content.capture());

        assertThat(content.getValue().appNotificationId()).isEqualTo(1L);
        assertThat(content.getValue().type()).isEqualTo(AppNotificationFeedType.TWIN_VIEW);
        assertThat(content.getValue().targetKind()).isEqualTo(AppNotificationFeedTargetType.PROFILE);
        assertThat(content.getValue().targetId()).isEqualTo(10L);
        then(fcmSender).should().send(anyList());
    }

    @Test
    @DisplayName("푸시를 끄거나 기기가 없는 유저도 앱 알림은 받고 푸시만 건너뛴다")
    void creates_feed_even_when_push_recipient_is_absent() {
        // given: 열람자는 있지만 수신 가능한 기기가 없다
        given(twinViewRepository.countDistinctViewersByViewedAtRange(any(), any()))
                .willReturn(List.of(viewerCount(10L, 3L)));
        givenSaveAllAssignsIds();
        given(pushRecipientResolver.resolve(anyList(), eq(NotificationType.EVENT))).willReturn(List.of());

        // when: 발송일에 실행
        twinViewNotificationService.notifyViewerCounts(SENDING_DAY);

        // then: 앱 알림은 저장되고 푸시는 만들지도 보내지도 않는다
        then(appNotificationFeedRepository).should(times(1)).saveAll(anyList());
        then(pushMessageBuilder).should(never()).feed(anyList(), any());
        then(fcmSender).should(never()).send(anyList());
    }

    private void givenSaveAllAssignsIds() {
        AtomicLong sequence = new AtomicLong();

        given(appNotificationFeedRepository.saveAll(anyList())).willAnswer(invocation -> {
            List<AppNotificationFeed> feeds = invocation.getArgument(0);
            feeds.forEach(feed -> ReflectionTestUtils.setField(feed, "id", sequence.incrementAndGet()));
            return feeds;
        });
    }

    private Instant kstEightPm(LocalDate date) {
        return date.atTime(LocalTime.of(20, 0)).atZone(KstTimes.ZONE).toInstant();
    }

    private ViewerCountProjection viewerCount(Long targetUserId, Long viewerCount) {
        return new ViewerCountProjection() {
            @Override
            public Long getTargetUserId() {
                return targetUserId;
            }

            @Override
            public Long getViewerCount() {
                return viewerCount;
            }
        };
    }

    private Device device(Long userId, String pushToken) {
        return Device.create(userId, UUID.randomUUID(), DevicePlatform.IOS, pushToken);
    }

    private PushMessage pushMessage(String token) {
        return new PushMessage(1L, PushType.TWIN_VIEW, token, Message.builder().setToken(token).build());
    }
}
