package com.nidus.twinly.notification.service;

import com.nidus.twinly.notification.dto.command.AppNotificationNoticePublishCommand;
import com.nidus.twinly.notification.repository.AppNotificationFeedRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AppNotificationNoticeServiceUnitTest {

    @Mock
    AppNotificationFeedRepository appNotificationFeedRepository;

    @InjectMocks
    AppNotificationNoticeService appNotificationNoticeService;

    @Test
    @DisplayName("대상이 없으면 활성 가입자 전체에게 발행하고 발행 수를 돌려준다")
    void publish_withoutUserIds_insertsForAllActiveUsers() {
        // given: 전체 발행 쿼리가 3건을 넣었다고 응답
        given(appNotificationFeedRepository.insertNoticeForActiveUsers("제목", "본문")).willReturn(3);

        // when: 대상 없이 공지 발행
        var result = appNotificationNoticeService.publish(new AppNotificationNoticePublishCommand("제목", "본문", null));

        // then: 전체 발행 쿼리 결과가 발행 수로 나오고, 대상 지정 쿼리는 호출되지 않음
        assertThat(result.publishedCount()).isEqualTo(3);
        then(appNotificationFeedRepository).should(never()).insertNoticeForActiveUsersIn(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("대상이 있으면 그 유저들에게만 발행한다")
    void publish_withUserIds_insertsForGivenUsers() {
        // given: 대상 지정 쿼리가 2건을 넣었다고 응답
        given(appNotificationFeedRepository.insertNoticeForActiveUsersIn("제목", "본문", List.of(1L, 2L))).willReturn(2);

        // when: 유저 1·2 를 지정해 공지 발행
        var result = appNotificationNoticeService.publish(new AppNotificationNoticePublishCommand("제목", "본문", List.of(1L, 2L)));

        // then: 대상 지정 쿼리 결과가 발행 수로 나오고, 전체 발행 쿼리는 호출되지 않음
        assertThat(result.publishedCount()).isEqualTo(2);
        then(appNotificationFeedRepository).should(never()).insertNoticeForActiveUsers(anyString(), anyString());
    }
}
