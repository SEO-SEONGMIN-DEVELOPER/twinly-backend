package com.nidus.twinly.me.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.common.feedback.FeedbackType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MeSendFeedbackRequest(
        @NotNull FeedbackType type,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @NotNull List<@NotNull Long> optionIds,
        @Schema(nullable = true)
        @Size(max = 500) String detail
) {
}
