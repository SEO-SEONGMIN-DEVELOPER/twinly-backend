package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeSchoolVerificationStudentIdPresignRequest;

public record MeSchoolVerificationStudentIdPresignCommand(
        String contentType
) {

    public static MeSchoolVerificationStudentIdPresignCommand from(MeSchoolVerificationStudentIdPresignRequest request) {
        return new MeSchoolVerificationStudentIdPresignCommand(request.contentType());
    }
}
