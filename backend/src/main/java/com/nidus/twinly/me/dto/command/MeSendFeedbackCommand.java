package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.common.feedback.FeedbackType;
import com.nidus.twinly.me.dto.request.MeSendFeedbackRequest;

import java.util.List;

public record MeSendFeedbackCommand(
        FeedbackType type,
        List<Long> optionIds,
        String detail
) {

    public static MeSendFeedbackCommand from(MeSendFeedbackRequest request) {
        return new MeSendFeedbackCommand(
                request.type(),
                request.optionIds(),
                request.detail()
        );
    }
}
