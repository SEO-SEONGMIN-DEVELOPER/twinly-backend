package com.nidus.twinly.notification.notifier;

import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.fcm.FcmSender;
import com.nidus.twinly.common.fcm.PushMessage;
import com.nidus.twinly.common.fcm.PushMessageBuilder;
import com.nidus.twinly.common.fcm.PushRecipientResolver;
import com.nidus.twinly.common.fcm.PushType;
import com.nidus.twinly.device.domain.DevicePlatform;
import com.nidus.twinly.device.entity.Device;
import com.nidus.twinly.notification.domain.AppNotificationFeedType;
import com.nidus.twinly.notification.domain.NotificationType;
import com.nidus.twinly.notification.entity.AppNotificationFeed;
import com.nidus.twinly.notification.event.AppNotificationFeedCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
class AppNotificationFeedPushNotifierUnitTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-20T03:00:00Z");

    @Mock
    PushRecipientResolver pushRecipientResolver;

    @Mock
    CloudFrontService cloudFrontService;

    @Mock
    FcmSender fcmSender;

    AppNotificationFeedPushNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new AppNotificationFeedPushNotifier(
                pushRecipientResolver, new PushMessageBuilder(cloudFrontService), fcmSender);
    }

    @Test
    @DisplayName("공지 피드는 수신자를 한 번에 조회해 EVENT 설정을 켠 기기로만 notice 푸시를 보낸다")
    void notice_resolves_once_and_skips_users_without_devices() {
        // given: 공지를 받은 유저 1·2, 그중 1번만 기기가 있음
        List<AppNotificationFeed> feeds = List.of(noticeFeed(1001L, 1L), noticeFeed(1002L, 2L));
        given(pushRecipientResolver.resolve(List.of(1L, 2L), NotificationType.EVENT))
                .willReturn(List.of(device(1L, "token-1")));

        // when: 피드 생성 이벤트 수신
        notifier.onAppNotificationFeedCreated(new AppNotificationFeedCreatedEvent(feeds));

        // then: 유저마다 조회하지 않고, 기기가 있는 1번에게만 notice 로 나간다
        ArgumentCaptor<List<PushMessage>> captor = messagesCaptor();
        then(pushRecipientResolver).should(times(1)).resolve(anyList(), any());
        then(fcmSender).should().send(captor.capture());
        assertThat(captor.getValue())
                .extracting(PushMessage::userId, PushMessage::type, PushMessage::token)
                .containsExactly(tuple(1L, PushType.NOTICE, "token-1"));
    }

    @Test
    @DisplayName("메시지가 500개를 넘으면 FCM 한도에 맞춰 500개씩 나눠 보낸다")
    void splits_into_batches_of_500() {
        // given: 기기 한 대씩 가진 유저 501명
        List<Long> userIds = LongStream.rangeClosed(1, 501).boxed().toList();
        List<AppNotificationFeed> feeds = userIds.stream().map(userId -> noticeFeed(userId + 1000, userId)).toList();
        given(pushRecipientResolver.resolve(userIds, NotificationType.EVENT))
                .willReturn(userIds.stream().map(userId -> device(userId, "token-" + userId)).toList());

        // when: 피드 생성 이벤트 수신
        notifier.onAppNotificationFeedCreated(new AppNotificationFeedCreatedEvent(feeds));

        // then: sendEach 는 한 번에 500개까지만 받으므로 500 + 1 로 나뉜다
        ArgumentCaptor<List<PushMessage>> captor = messagesCaptor();
        then(fcmSender).should(times(2)).send(captor.capture());
        assertThat(captor.getAllValues()).extracting(List::size).containsExactly(500, 1);
    }

    @Test
    @DisplayName("받을 기기가 하나도 없으면 FCM 을 호출하지 않는다")
    void skips_send_when_no_devices() {
        // given: 공지를 받은 유저가 모두 푸시를 껐거나 기기가 없음
        given(pushRecipientResolver.resolve(List.of(1L), NotificationType.EVENT)).willReturn(List.of());

        // when: 피드 생성 이벤트 수신
        notifier.onAppNotificationFeedCreated(new AppNotificationFeedCreatedEvent(List.of(noticeFeed(1001L, 1L))));

        // then: 발송하지 않는다
        then(fcmSender).should(never()).send(anyList());
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<PushMessage>> messagesCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }

    private AppNotificationFeed noticeFeed(Long feedId, Long userId) {
        AppNotificationFeed feed = mock(AppNotificationFeed.class, withSettings().strictness(Strictness.LENIENT));
        given(feed.getId()).willReturn(feedId);
        given(feed.getUserId()).willReturn(userId);
        given(feed.getType()).willReturn(AppNotificationFeedType.NOTICE);
        given(feed.getTitle()).willReturn("공지 제목");
        given(feed.getBody()).willReturn("공지 본문");
        given(feed.getCreatedAt()).willReturn(CREATED_AT);
        return feed;
    }

    private Device device(Long userId, String token) {
        return Device.create(userId, UUID.randomUUID(), DevicePlatform.IOS, token);
    }
}
