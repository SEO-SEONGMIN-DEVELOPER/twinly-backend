package com.nidus.twinly.admin.dto.result;

import com.nidus.twinly.simulation.client.SimulationBatchCostBody;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    private static final AdminGenderCountResult NO_MEMBERS = new AdminGenderCountResult(0, 0, 0);

    public static AdminBatchCostResult from(SimulationBatchCostBody body, Map<Integer, AdminGenderCountResult> membersByGroup) {
        List<Group> groups = body.groups() == null ? List.of() : body.groups().stream()
                .map(group -> Group.from(group, membersByGroup.getOrDefault(group.groupNumber(), NO_MEMBERS)))
                .toList();
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
            Integer unpricedResponseCount,
            AdminGenderCountResult members
    ) {

        static Group from(SimulationBatchCostBody.Group group, AdminGenderCountResult members) {
            return new Group(
                    group.groupNumber(),
                    group.worldTime(),
                    group.simulationDate(),
                    group.participantCount(),
                    group.totalCostUsd(),
                    group.averageCostUsd(),
                    group.unpricedResponseCount(),
                    members
            );
        }
    }
}
