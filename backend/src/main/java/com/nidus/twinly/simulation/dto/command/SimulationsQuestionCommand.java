package com.nidus.twinly.simulation.dto.command;

import com.nidus.twinly.activity.domain.QuestionType;
import com.nidus.twinly.simulation.dto.request.SimulationsQuestionRequest;

import java.time.LocalTime;
import java.util.List;

public record SimulationsQuestionCommand(
        LocalTime time,
        QuestionType qtype,
        List<Long> partnerId,
        String text,
        List<String> options
) {

    public static SimulationsQuestionCommand from(SimulationsQuestionRequest request) {
        return new SimulationsQuestionCommand(
                request.time(),
                request.qtype(),
                request.partnerId(),
                request.text(),
                request.options()
        );
    }
}
