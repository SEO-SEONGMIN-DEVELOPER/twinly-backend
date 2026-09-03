package com.nidus.twinly.app.service;

import com.nidus.twinly.app.domain.MaintenanceState;
import com.nidus.twinly.app.dto.command.MaintenanceUpdateCommand;
import com.nidus.twinly.app.store.AppBlockPolicyStore;
import com.nidus.twinly.connection.domain.ConnectionDrainingReason;
import com.nidus.twinly.connection.domain.ConnectionDrainingScope;
import com.nidus.twinly.connection.dto.command.ConnectionDrainingCommand;
import com.nidus.twinly.connection.service.ConnectionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AppBlockPolicyServiceUnitTest {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");

    @Mock
    AppBlockPolicyStore store;

    @Mock
    ConnectionService connectionService;

    AppBlockPolicyService service() {
        return new AppBlockPolicyService(store, connectionService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("점검을 켜면 저장한 뒤 열린 소켓에 maintenance 사유로 draining을 전체 전파한다 (저장이 먼저여야 앱의 probe가 503을 받는다)")
    void activate_savesThenNotifiesDraining() {
        // given: 10분 뒤 종료 예정
        Instant until = NOW.plusSeconds(600);

        // when
        service().updateMaintenance(new MaintenanceUpdateCommand(true, "점검 중이에요.", until));

        // then
        InOrder inOrder = inOrder(store, connectionService);
        inOrder.verify(store).saveMaintenance(new MaintenanceState(true, "점검 중이에요.", until));
        inOrder.verify(connectionService).notifyDraining(new ConnectionDrainingCommand(
                ConnectionDrainingReason.MAINTENANCE, 600_000L, ConnectionDrainingScope.ALL));
    }

    @Test
    @DisplayName("종료 예정이 없으면 retryAfterMs 없이 전파한다")
    void activate_withoutUntil_sendsNullRetryAfter() {
        // when
        service().updateMaintenance(new MaintenanceUpdateCommand(true, null, null));

        // then
        then(connectionService).should().notifyDraining(new ConnectionDrainingCommand(
                ConnectionDrainingReason.MAINTENANCE, null, ConnectionDrainingScope.ALL));
    }

    @Test
    @DisplayName("종료 예정이 15분보다 멀면 계약 상한 900000ms로 자른다")
    void activate_capsRetryAfterAtContractMax() {
        // when
        service().updateMaintenance(new MaintenanceUpdateCommand(true, null, NOW.plusSeconds(3600)));

        // then
        then(connectionService).should().notifyDraining(new ConnectionDrainingCommand(
                ConnectionDrainingReason.MAINTENANCE, 900_000L, ConnectionDrainingScope.ALL));
    }

    @Test
    @DisplayName("종료 예정이 이미 지났으면 retryAfterMs 없이 전파한다")
    void activate_pastUntil_sendsNullRetryAfter() {
        // when
        service().updateMaintenance(new MaintenanceUpdateCommand(true, null, NOW.minusSeconds(1)));

        // then
        then(connectionService).should().notifyDraining(new ConnectionDrainingCommand(
                ConnectionDrainingReason.MAINTENANCE, null, ConnectionDrainingScope.ALL));
    }

    @Test
    @DisplayName("점검을 끄면 저장만 하고 draining은 보내지 않는다")
    void deactivate_savesWithoutDraining() {
        // when
        service().updateMaintenance(new MaintenanceUpdateCommand(false, null, null));

        // then
        then(store).should().saveMaintenance(MaintenanceState.none());
        then(connectionService).should(never()).notifyDraining(any());
    }
}
