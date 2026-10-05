package com.nidus.twinly.balancegame.service;

import com.nidus.twinly.balancegame.entity.BalanceGameRound;
import com.nidus.twinly.balancegame.event.BalanceGameSummaryEvent;
import com.nidus.twinly.balancegame.repository.BalanceGameAnswerRepository;
import com.nidus.twinly.balancegame.repository.BalanceGameAnswerRepository.MatchedCountProjection;
import com.nidus.twinly.balancegame.repository.BalanceGameRoundRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class BalanceGameSummaryServiceUnitTest {

    private static final Instant NOW = Instant.parse("2026-10-03T03:00:10Z");
    private static final Long ROUND_ID = 100L;

    @Mock
    BalanceGameRoundRepository balanceGameRoundRepository;

    @Mock
    BalanceGameAnswerRepository balanceGameAnswerRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    BalanceGameSummaryService balanceGameSummaryService;

    @Test
    @DisplayName("KST 12시 10초에 방금 끝난 어제 18시 회차를 한 번만 요약해, 같은 답을 고른 상대가 있는 유저별 인원을 이벤트로 낸다")
    void sends_summary_of_round_ended_within_last_hour() {
        // given: 방금 끝난 회차, 10번은 3명·20번은 1명과 일치
        givenEndedRound();
        given(balanceGameRoundRepository.markSummarySent(ROUND_ID, NOW)).willReturn(1);
        given(balanceGameAnswerRepository.countMatchesByUserInRound(ROUND_ID))
                .willReturn(List.of(count(10L, 3L), count(20L, 1L)));

        // when
        balanceGameSummaryService.sendEndedRound(NOW);

        // then
        then(eventPublisher).should().publishEvent(new BalanceGameSummaryEvent(ROUND_ID, Map.of(10L, 3L, 20L, 1L), NOW));
    }

    @Test
    @DisplayName("다른 서버가 먼저 요약을 맡았으면 아무것도 보내지 않는다")
    void skips_round_already_claimed() {
        // given: 표시 갱신이 0건
        givenEndedRound();
        given(balanceGameRoundRepository.markSummarySent(ROUND_ID, NOW)).willReturn(0);

        // when
        balanceGameSummaryService.sendEndedRound(NOW);

        // then
        then(balanceGameAnswerRepository).should(never()).countMatchesByUserInRound(anyLong());
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("같은 답을 고른 쌍이 하나도 없으면 이벤트를 내지 않는다")
    void skips_round_without_match() {
        // given
        givenEndedRound();
        given(balanceGameRoundRepository.markSummarySent(ROUND_ID, NOW)).willReturn(1);
        given(balanceGameAnswerRepository.countMatchesByUserInRound(ROUND_ID)).willReturn(List.of());

        // when
        balanceGameSummaryService.sendEndedRound(NOW);

        // then
        then(eventPublisher).should(never()).publishEvent(any());
    }

    private void givenEndedRound() {
        BalanceGameRound round = BeanUtils.instantiateClass(BalanceGameRound.class);
        ReflectionTestUtils.setField(round, "id", ROUND_ID);
        ReflectionTestUtils.setField(round, "startsAt", Instant.parse("2026-10-02T09:00:00Z"));
        given(balanceGameRoundRepository.findByStartsAt(Instant.parse("2026-10-02T09:00:00Z")))
                .willReturn(Optional.of(round));
    }

    private MatchedCountProjection count(Long userId, Long matchedCount) {
        return new MatchedCountProjection() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public Long getMatchedCount() {
                return matchedCount;
            }
        };
    }
}
