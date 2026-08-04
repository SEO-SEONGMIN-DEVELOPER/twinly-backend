package com.nidus.twinly.simulation.dto.command;

import com.nidus.twinly.simulation.dto.request.SimulationsRelationshipRequest;

import java.time.LocalTime;

public record SimulationsRelationshipCommand(
        Long partnerId,
        LocalTime updateTime,
        Integer rapport,
        String partnerModel
) {

    public static SimulationsRelationshipCommand from(SimulationsRelationshipRequest request) {
        return new SimulationsRelationshipCommand(
                request.partnerId(),
                request.updateTime(),
                request.rapport(),
                request.partnerModel()
        );
    }
}
