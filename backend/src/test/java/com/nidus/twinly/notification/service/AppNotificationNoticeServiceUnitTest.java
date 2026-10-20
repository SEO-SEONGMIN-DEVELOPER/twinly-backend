package com.nidus.twinly.notification.service;

import com.nidus.twinly.notification.domain.AppNotificationFeedType;
import com.nidus.twinly.notification.dto.command.AppNotificationNoticePublishCommand;
import com.nidus.twinly.notification.entity.AppNotificationFeed;
import com.nidus.twinly.notification.event.AppNotificationFeedCreatedEvent;
import com.nidus.twinly.notification.repository.AppNotificationFeedRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AppNotificationNoticeServiceUnitTest {

    private static final Long LAST_FEED_ID = 100L;

    @Mock
    AppNotificationFeedRepository appNotificationFeedRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    AppNotificationNoticeService appNotificationNoticeService;

    @Test
    @DisplayName("대상이 없으면 활성 가입자 전체에게 발행하고 발행 수를 돌려준다")
    void publish_withoutUserIds_insertsForAllActiveUsers() {
        // given: 전체 발행 쿼리가 3건을 넣었다고 응답
        given(appNotificationFeedRepository.findMaxId()).willReturn(LAST_FEED_ID);
        given(appNotificationFeedRepository.insertNoticeForActiveUsers("제목", "본문")).willReturn(3);

        // when: 대상 없이 공지 발행
        var result = appNotificationNoticeService.publish(new AppNotificationNoticePublishCommand("제목", "본문", true, null));

        // then: 전체 발행 쿼리 결과가 발행 수로 나오고, 대상 지정 쿼리는 호출되지 않음
        assertThat(result.publishedCount()).isEqualTo(3);
        then(appNotificationFeedRepository).should(never()).insertNoticeForActiveUsersIn(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("대상이 있으면 그 유저들에게만 발행한다")
    void publish_withUserIds_insertsForGivenUsers() {
        // given: 대상 지정 쿼리가 2건을 넣었다고 응답
        given(appNotificationFeedRepository.findMaxId()).willReturn(LAST_FEED_ID);
        given(appNotificationFeedRepository.insertNoticeForActiveUsersIn("제목", "본문", List.of(1L, 2L))).willReturn(2);

        // when: 유저 1·2 를 지정해 공지 발행
        var result = appNotificationNoticeService.publish(new AppNotificationNoticePublishCommand("제목", "본문", true, List.of(1L, 2L)));

        // then: 대상 지정 쿼리 결과가 발행 수로 나오고, 전체 발행 쿼리는 호출되지 않음
        assertThat(result.publishedCount()).isEqualTo(2);
        then(appNotificationFeedRepository).should(never()).insertNoticeForActiveUsers(anyString(), anyString());
    }

    @Test
    @DisplayName("새로 들어간 공지가 있으면 발행 직전 마지막 id 이후의 같은 공지만 모아 푸시 이벤트로 넘긴다")
    void publish_inserted_publishesEventWithNewFeedsOnly() {
        // given: 발행 직전 마지막 피드 id 는 100, 2건이 새로 들어감
        List<AppNotificationFeed> newFeeds = List.of(mock(AppNotificationFeed.class), mock(AppNotificationFeed.class));
        given(appNotificationFeedRepository.findMaxId()).willReturn(LAST_FEED_ID);
        given(appNotificationFeedRepository.insertNoticeForActiveUsers("제목", "본문")).willReturn(2);
        given(appNotificationFeedRepository.findAllByIdGreaterThanAndTypeAndTitleAndBody(
                LAST_FEED_ID, AppNotificationFeedType.NOTICE, "제목", "본문")).willReturn(newFeeds);

        // when: 공지 발행
        appNotificationNoticeService.publish(new AppNotificationNoticePublishCommand("제목", "본문", true, null));

        // then: 이미 받은 유저의 예전 공지는 id 가 100 이하라 빠지고, 이번에 들어간 것만 푸시된다
        then(eventPublisher).should().publishEvent(new AppNotificationFeedCreatedEvent(newFeeds));
    }

    @Test
    @DisplayName("새로 들어간 공지가 없으면 조회도 이벤트 발행도 하지 않는다")
    void publish_nothingInserted_skipsEvent() {
        // given: 모두 이미 받은 공지라 0건
        given(appNotificationFeedRepository.findMaxId()).willReturn(LAST_FEED_ID);
        given(appNotificationFeedRepository.insertNoticeForActiveUsers("제목", "본문")).willReturn(0);

        // when: 같은 공지를 다시 발행
        var result = appNotificationNoticeService.publish(new AppNotificationNoticePublishCommand("제목", "본문", true, null));

        // then: 재호출로 푸시가 다시 나가면 안 된다
        assertThat(result.publishedCount()).isZero();
        then(appNotificationFeedRepository).should(never())
                .findAllByIdGreaterThanAndTypeAndTitleAndBody(any(), any(), anyString(), anyString());
        then(eventPublisher).should(never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("push 가 false 면 피드만 넣고 새 공지를 조회하지도 푸시 이벤트를 내지도 않는다")
    void publish_feedOnly_skipsEvent() {
        // given: 2건이 새로 들어감
        given(appNotificationFeedRepository.findMaxId()).willReturn(LAST_FEED_ID);
        given(appNotificationFeedRepository.insertNoticeForActiveUsers("제목", "본문")).willReturn(2);

        // when: 피드 전용으로 공지 발행
        var result = appNotificationNoticeService.publish(new AppNotificationNoticePublishCommand("제목", "본문", false, null));

        // then: 피드 발행 수는 그대로 나오고, 푸시 경로는 타지 않는다
        assertThat(result.publishedCount()).isEqualTo(2);
        then(appNotificationFeedRepository).should(never())
                .findAllByIdGreaterThanAndTypeAndTitleAndBody(any(), any(), anyString(), anyString());
        then(eventPublisher).should(never()).publishEvent(any(Object.class));
    }
}
