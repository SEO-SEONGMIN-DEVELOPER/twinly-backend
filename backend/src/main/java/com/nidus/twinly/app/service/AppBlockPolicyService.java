package com.nidus.twinly.app.service;

import com.nidus.twinly.app.domain.AppBlockPolicy;
import com.nidus.twinly.app.domain.AppVersionPolicy;
import com.nidus.twinly.app.domain.MaintenanceState;
import com.nidus.twinly.app.dto.command.AppVersionPolicyUpdateCommand;
import com.nidus.twinly.app.dto.command.MaintenanceUpdateCommand;
import com.nidus.twinly.app.store.AppBlockPolicyStore;
import com.nidus.twinly.connection.domain.ConnectionDrainingReason;
import com.nidus.twinly.connection.domain.ConnectionDrainingScope;
import com.nidus.twinly.connection.dto.command.ConnectionDrainingCommand;
import com.nidus.twinly.connection.service.ConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AppBlockPolicyService {

    private static final long MAX_RETRY_AFTER_MS = 900_000L;

    private final AppBlockPolicyStore appBlockPolicyStore;
    private final ConnectionService connectionService;
    private final Clock clock;

    public AppBlockPolicy current() {
        return appBlockPolicyStore.current();
    }

    public void updateMaintenance(MaintenanceUpdateCommand command) {
        appBlockPolicyStore.saveMaintenance(new MaintenanceState(command.active(), command.message(), command.until()));

        if (command.active()) {
            connectionService.notifyDraining(new ConnectionDrainingCommand(
                    ConnectionDrainingReason.MAINTENANCE,
                    retryAfterMs(command.until()),
                    ConnectionDrainingScope.ALL));
        }
    }

    public void updateVersionPolicy(AppVersionPolicyUpdateCommand command) {
        appBlockPolicyStore.saveVersionPolicy(command.platform(), new AppVersionPolicy(command.minVersion(), command.storeUrl()));
    }

    private Long retryAfterMs(Instant until) {
        if (until == null) {
            return null;
        }

        long millis = Duration.between(clock.instant(), until).toMillis();

        if (millis <= 0) {
            return null;
        }

        return Math.min(millis, MAX_RETRY_AFTER_MS);
    }
}
