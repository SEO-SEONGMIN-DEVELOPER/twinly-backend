package com.nidus.twinly.simulation.notifier;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.purchase.event.SimulationAccessGrantedEvent;
import com.nidus.twinly.simulation.client.SimulationPreloadClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class SimulationPreloadNotifierUnitTest {

    private static final Long USER_ID = 12L;
    private static final Instant KST_EARLY_MORNING_WITH_MILLIS = Instant.parse("2026-09-02T16:05:09.123Z");

    @Mock
    SimulationPreloadClient simulationPreloadClient;

    @InjectMocks
    SimulationPreloadNotifier notifier;

    @Test
    @DisplayName("권한 반영 시각을 KST 초 단위로 바꾸고, 그 날짜부터 이틀을 요청한다")
    void onSimulationAccessGranted_requests_two_days_from_granted_date_in_kst() {
        // given: UTC 9월 2일 16:05:09.123 = KST 9월 3일 01:05:09.123

        // when
        notifier.onSimulationAccessGranted(new SimulationAccessGrantedEvent(USER_ID, KST_EARLY_MORNING_WITH_MILLIS));

        // then: UTC 날짜(9/2)가 아닌 KST 날짜(9/3)부터 이틀, 시각은 밀리초가 잘린 KST
        then(simulationPreloadClient).should().preload(
                USER_ID,
                LocalDateTime.of(2026, 9, 3, 1, 5, 9),
                List.of(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 4)));
    }

    @Test
    @DisplayName("요청이 실패하면 최대 3회까지 재시도하고 성공하면 멈춘다")
    void onSimulationAccessGranted_retries_until_success() {
        // given: 두 번 실패 후 세 번째 성공
        willThrow(new BusinessException(ErrorCode.SIMULATION_PRELOAD_FAILED))
                .willThrow(new BusinessException(ErrorCode.SIMULATION_PRELOAD_FAILED))
                .willDoNothing()
                .given(simulationPreloadClient).preload(anyLong(), any(), anyList());

        // when
        notifier.onSimulationAccessGranted(new SimulationAccessGrantedEvent(USER_ID, KST_EARLY_MORNING_WITH_MILLIS));

        // then
        then(simulationPreloadClient).should(times(3)).preload(anyLong(), any(), anyList());
    }

    @Test
    @DisplayName("3회 모두 실패해도 예외를 밖으로 내보내지 않고 더 시도하지 않는다")
    void onSimulationAccessGranted_swallows_final_failure() {
        // given: 계속 실패
        willThrow(new BusinessException(ErrorCode.SIMULATION_PRELOAD_FAILED))
                .given(simulationPreloadClient).preload(anyLong(), any(), anyList());

        // when & then
        assertThatCode(() -> notifier.onSimulationAccessGranted(
                new SimulationAccessGrantedEvent(USER_ID, KST_EARLY_MORNING_WITH_MILLIS)))
                .doesNotThrowAnyException();
        then(simulationPreloadClient).should(times(3)).preload(anyLong(), any(), anyList());
    }
}
