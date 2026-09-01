package com.nidus.twinly.purchase.reader;

import com.nidus.twinly.purchase.repository.UserEntitlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class EntitlementReader {

    public static final String SIMULATION_ACCESS = "simulation_access";

    private final UserEntitlementRepository userEntitlementRepository;

    public boolean hasSimulationAccess(Long userId) {
        return userEntitlementRepository.existsActive(userId, SIMULATION_ACCESS, Instant.now());
    }

    public List<Long> userIdsWithSimulationAccess() {
        return userEntitlementRepository.findUserIdsActive(SIMULATION_ACCESS, Instant.now());
    }
}
