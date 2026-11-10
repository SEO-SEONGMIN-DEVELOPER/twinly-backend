package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeStatusResult;
import com.nidus.twinly.organization.domain.OrganizationType;

public record MeStatusResponse(
        OrganizationType organizationType,
        MeStatusWithdrawalResponse withdrawal,
        MeStatusReportResponse report,
        MeStatusPersonaResponse persona,
        MeStatusSchoolVerificationResponse schoolVerification
) {

    public static MeStatusResponse from(MeStatusResult result) {
        return new MeStatusResponse(
                result.organizationType(),
                MeStatusWithdrawalResponse.from(result.withdrawal()),
                MeStatusReportResponse.from(result.report()),
                MeStatusPersonaResponse.from(result.persona()),
                MeStatusSchoolVerificationResponse.from(result.schoolVerification())
        );
    }
}
