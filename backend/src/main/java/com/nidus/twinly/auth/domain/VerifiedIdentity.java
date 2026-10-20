package com.nidus.twinly.auth.domain;

import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.domain.MobileCarrier;
import com.nidus.twinly.common.domain.NationalInfo;

import java.time.LocalDate;

public record VerifiedIdentity(
        String name,
        LocalDate birthDate,
        Gender gender,
        NationalInfo nationalInfo,
        String phoneNumber,
        MobileCarrier mobileCarrier,
        String di
) {
}
