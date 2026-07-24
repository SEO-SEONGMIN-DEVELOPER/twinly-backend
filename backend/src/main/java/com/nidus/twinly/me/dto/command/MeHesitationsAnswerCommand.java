package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeHesitationsAnswerRequest;

public record MeHesitationsAnswerCommand(
        String answer,
        Boolean skipped
) {

    public static MeHesitationsAnswerCommand from(MeHesitationsAnswerRequest request) {
        return new MeHesitationsAnswerCommand(request.answer(), request.skipped());
    }
}
