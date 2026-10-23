package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeSchoolVerificationStudentIdSubmitRequest;

public record MeSchoolVerificationStudentIdSubmitCommand(
        String key
) {

    public static MeSchoolVerificationStudentIdSubmitCommand from(MeSchoolVerificationStudentIdSubmitRequest request) {
        return new MeSchoolVerificationStudentIdSubmitCommand(request.key());
    }
}
