package com.nidus.twinly.simulation.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.activity.domain.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.List;

public record SimulationsQuestionRequest(
        @NotNull LocalTime time,
        @NotNull QuestionType qtype,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        List<Long> partnerId,
        @NotBlank String text,
        @NotNull List<String> options
) {
}
