package com.nidus.twinly.main.service;

import com.nidus.twinly.chat.repository.ChatRepository;
import com.nidus.twinly.main.dto.result.MainTabResult;
import com.nidus.twinly.notification.repository.AppNotificationFeedRepository;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class MainServiceUnitTest {

    private static final Long CURRENT_SEASON_ID = 1L;
    private static final Long USER_ID = 10L;

    @Mock
    CurrentSeasonReader currentSeasonReader;

    @Mock
    ChatRepository chatRepository;

    @Mock
    AppNotificationFeedRepository appNotificationFeedRepository;

    @InjectMocks
    MainService mainService;

    @Test
    @DisplayName("진행 중인 시즌이면 경과 비율을 퍼센트 문자열로 만들고 미읽음 개수를 함께 반환한다")
    void mainTab_returns_progress_and_unread_counts() {
        // given: 총 100일 중 25일이 지난 시즌 + 안읽은 채팅방 3개 / 알림 5건
        Instant now = Instant.now();
        given(currentSeasonReader.read()).willReturn(
                season(CURRENT_SEASON_ID, now.minus(Duration.ofDays(25)), now.plus(Duration.ofDays(75))));
        given(chatRepository.countUnreadRoomsByUserId(USER_ID)).willReturn(3);
        given(appNotificationFeedRepository.countByUserIdAndReadAtIsNull(USER_ID)).willReturn(5);

        // when: 메인 탭 조회
        MainTabResult result = mainService.mainTab(USER_ID);

        // then: 진행률 25% + 미읽음 개수가 그대로 담기고 serverNow는 현재 시각이다
        assertThat(result.season().seasonId()).isEqualTo(CURRENT_SEASON_ID);
        assertThat(result.season().progress()).isEqualTo("25%");
        assertThat(result.season().serverNow()).isBetween(now, Instant.now());
        assertThat(result.unreadChatRoomCount()).isEqualTo(3);
        assertThat(result.unreadNotificationCount()).isEqualTo(5);

        // then: 미읽음 집계는 인증 유저 id로 각 리포지토리에 위임한다
        then(chatRepository).should().countUnreadRoomsByUserId(USER_ID);
        then(appNotificationFeedRepository).should().countByUserIdAndReadAtIsNull(USER_ID);
    }

    @Test
    @DisplayName("이미 종료된 시즌이면 진행률이 100%로 고정된다")
    void mainTab_after_season_end_clamps_progress_to_100() {
        // given: 20일 전에 시작해 10일 전에 끝난 시즌 (경과 비율이 100%를 넘음)
        Instant now = Instant.now();
        given(currentSeasonReader.read()).willReturn(
                season(CURRENT_SEASON_ID, now.minus(Duration.ofDays(20)), now.minus(Duration.ofDays(10))));

        // when: 메인 탭 조회
        MainTabResult result = mainService.mainTab(USER_ID);

        // then: 상한 클램프가 걸려 100%
        assertThat(result.season().progress()).isEqualTo("100%");
    }

    @Test
    @DisplayName("아직 시작하지 않은 시즌이면 진행률이 0%로 고정된다")
    void mainTab_before_season_start_clamps_progress_to_0() {
        // given: 10일 뒤 시작해 20일 뒤 끝나는 시즌 (경과 시간이 음수)
        Instant now = Instant.now();
        given(currentSeasonReader.read()).willReturn(
                season(CURRENT_SEASON_ID, now.plus(Duration.ofDays(10)), now.plus(Duration.ofDays(20))));

        // when: 메인 탭 조회
        MainTabResult result = mainService.mainTab(USER_ID);

        // then: 하한 클램프가 걸려 0%
        assertThat(result.season().progress()).isEqualTo("0%");
    }

    @Test
    @DisplayName("활성 시즌이 없으면 IllegalStateException이 발생하고 미읽음 집계는 하지 않는다")
    void mainTab_when_current_season_not_found_throws() {
        // given: 활성화된 시즌이 DB에 없어 조회 단계에서 예외가 난다
        given(currentSeasonReader.read()).willThrow(new IllegalStateException("활성화된 시즌이 존재하지 않습니다."));

        // when & then: IllegalStateException 발생 + 뒤따르는 집계 쿼리는 호출되지 않음
        assertThatThrownBy(() -> mainService.mainTab(USER_ID))
                .isInstanceOf(IllegalStateException.class);

        then(chatRepository).shouldHaveNoInteractions();
        then(appNotificationFeedRepository).shouldHaveNoInteractions();
    }

    /** Season은 팩토리·세터가 없으므로 protected 기본 생성자로 만든 뒤 필드를 직접 채운다. */
    private Season season(Long id, Instant startedAt, Instant endedAt) {
        Season season = BeanUtils.instantiateClass(Season.class);
        ReflectionTestUtils.setField(season, "id", id);
        ReflectionTestUtils.setField(season, "startedAt", startedAt);
        ReflectionTestUtils.setField(season, "endedAt", endedAt);
        return season;
    }
}
