package com.nidus.twinly.simulation.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SimulationBatchCostBody(
        LocalDate simulationDate,
        Integer groupCount,
        Integer startedGroupCount,
        Integer participantCount,
        BigDecimal totalCostUsd,
        BigDecimal averageCostUsd,
        Integer unpricedResponseCount,
        List<Group> groups
) {

    public record Group(
            Integer groupNumber,
            LocalDateTime worldTime,
            LocalDate simulationDate,
            Integer participantCount,
            BigDecimal totalCostUsd,
            BigDecimal averageCostUsd,
            Integer unpricedResponseCount
    ) {
    }
}
