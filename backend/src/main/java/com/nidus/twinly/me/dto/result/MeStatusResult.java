package com.nidus.twinly.me.dto.result;

import com.nidus.twinly.organization.domain.OrganizationType;

public record MeStatusResult(
        OrganizationType organizationType,
        MeStatusWithdrawalResult withdrawal,
        MeStatusReportResult report,
        MeStatusPersonaResult persona,
        MeStatusSchoolVerificationResult schoolVerification
) {
}
