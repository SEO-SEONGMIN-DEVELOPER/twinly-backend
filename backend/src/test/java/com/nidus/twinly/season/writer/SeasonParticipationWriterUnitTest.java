package com.nidus.twinly.season.writer;

import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SeasonParticipationWriterUnitTest {

    private static final Long CURRENT_SEASON_ID = 1L;
    private static final Long NEW_SEASON_ID = 2L;

    @Mock
    CurrentSeasonReader currentSeasonReader;

    @Mock
    EntitlementReader entitlementReader;

    @Mock
    SeasonParticipationRepository seasonParticipationRepository;

    @InjectMocks
    SeasonParticipationWriter seasonParticipationWriter;

    @Test
    @DisplayName("현재 시즌 참가는 조회 없이 upsert 한 번으로 위임한다")
    void participateInCurrentSeason_delegates_to_upsert() {
        // given: 활성 시즌이 있음
        given(currentSeasonReader.read()).willReturn(season());

        // when: 현재 시즌 참가
        seasonParticipationWriter.participateInCurrentSeason(10L);

        // then: 동기화가 반복돼도 유니크 제약을 위반하지 않도록 원자적 upsert 한 번
        then(seasonParticipationRepository).should().upsert(10L, CURRENT_SEASON_ID);
        then(seasonParticipationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("활성 시즌이 없으면 참가를 저장하지 않고 예외가 전파된다")
    void participateInCurrentSeason_when_current_season_missing_throws() {
        // given: 활성 시즌이 없는 비정상 상태
        given(currentSeasonReader.read()).willThrow(new IllegalStateException("활성화된 시즌이 존재하지 않습니다."));

        // when & then: 참가 행을 만들지 않는다 (구매 동기화 재시도로 복구된다)
        assertThatThrownBy(() -> seasonParticipationWriter.participateInCurrentSeason(10L))
                .isInstanceOf(IllegalStateException.class);

        then(seasonParticipationRepository).should(never()).upsert(any(), any());
    }

    @Test
    @DisplayName("결제 상태인 유저 전원을 지정한 시즌에 참가시킨다")
    void participateAllWithSimulationAccess_upserts_every_entitled_user() {
        // given: simulation_access 가 살아 있는 유저 두 명
        given(entitlementReader.userIdsWithSimulationAccess()).willReturn(List.of(10L, 20L));

        // when: 새 시즌으로 일괄 참가
        seasonParticipationWriter.participateAllWithSimulationAccess(NEW_SEASON_ID);

        // then: 각각 새 시즌 참가 행이 생긴다
        then(seasonParticipationRepository).should().upsert(10L, NEW_SEASON_ID);
        then(seasonParticipationRepository).should().upsert(20L, NEW_SEASON_ID);
    }

    private Season season() {
        Season season = BeanUtils.instantiateClass(Season.class);
        ReflectionTestUtils.setField(season, "id", CURRENT_SEASON_ID);
        return season;
    }
}
