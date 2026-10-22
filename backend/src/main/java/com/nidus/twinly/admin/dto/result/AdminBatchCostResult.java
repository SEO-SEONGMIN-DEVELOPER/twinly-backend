package com.nidus.twinly.admin.dto.result;

import com.nidus.twinly.simulation.client.SimulationBatchCostBody;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AdminBatchCostResult(
        LocalDate simulationDate,
        Integer groupCount,
        Integer startedGroupCount,
        Integer participantCount,
        BigDecimal totalCostUsd,
        BigDecimal averageCostUsd,
        Integer unpricedResponseCount,
        List<Group> groups
) {

    public static AdminBatchCostResult from(SimulationBatchCostBody body) {
        List<Group> groups = body.groups() == null ? List.of() : body.groups().stream().map(Group::from).toList();
        return new AdminBatchCostResult(
                body.simulationDate(),
                body.groupCount(),
                body.startedGroupCount(),
                body.participantCount(),
                body.totalCostUsd(),
                body.averageCostUsd(),
                body.unpricedResponseCount(),
                groups
        );
    }

    public record Group(
            Integer groupNumber,
            LocalDateTime worldTime,
            LocalDate simulationDate,
            Integer participantCount,
            BigDecimal totalCostUsd,
            BigDecimal averageCostUsd,
            Integer unpricedResponseCount
    ) {

        static Group from(SimulationBatchCostBody.Group group) {
            return new Group(
                    group.groupNumber(),
                    group.worldTime(),
                    group.simulationDate(),
                    group.participantCount(),
                    group.totalCostUsd(),
                    group.averageCostUsd(),
                    group.unpricedResponseCount()
            );
        }
    }
}
