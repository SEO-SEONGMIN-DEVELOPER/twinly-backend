package com.nidus.twinly.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nidus.twinly.auth.domain.InvalidIdentityResultException;
import com.nidus.twinly.auth.domain.VerifiedIdentity;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.domain.MobileCarrier;
import com.nidus.twinly.common.domain.NationalInfo;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public record NiceAuthResult(
        @JsonProperty("name")
        String name,
        @JsonProperty("birthdate")
        String birthdate,
        @JsonProperty("gender")
        String gender,
        @JsonProperty("national_info")
        String nationalInfo,
        @JsonProperty("di")
        String di,
        @JsonProperty("mobile_co")
        String mobileCo,
        @JsonProperty("mobile_no")
        String mobileNo
) {

    public VerifiedIdentity toVerifiedIdentity() {
        List<String> missing = Stream.of(
                        Map.entry("name", Objects.toString(name, "")),
                        Map.entry("birthdate", Objects.toString(birthdate, "")),
                        Map.entry("gender", Objects.toString(gender, "")),
                        Map.entry("di", Objects.toString(di, "")),
                        Map.entry("mobile_no", Objects.toString(mobileNo, "")))
                .filter(entry -> entry.getValue().isBlank())
                .map(Map.Entry::getKey)
                .toList();

        if (!missing.isEmpty()) {
            throw new InvalidIdentityResultException("NICE 인증 결과에 필수 항목이 없습니다. missing=" + missing, di);
        }

        Gender parsedGender = Gender.fromNiceCode(gender);
        NationalInfo parsedNationalInfo = NationalInfo.fromNiceCode(nationalInfo);
        MobileCarrier parsedMobileCarrier = MobileCarrier.fromNiceCode(mobileCo);

        if (parsedGender == null || parsedNationalInfo == null || parsedMobileCarrier == null) {
            throw new InvalidIdentityResultException("NICE 인증 결과의 코드값을 해석할 수 없습니다. gender=%s, nationalInfo=%s, mobileCo=%s"
                    .formatted(gender, nationalInfo, mobileCo), di);
        }

        LocalDate parsedBirthDate;
        try {
            parsedBirthDate = LocalDate.parse(birthdate, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            throw new InvalidIdentityResultException("NICE 인증 결과의 생년월일 형식을 해석할 수 없습니다.", di);
        }

        return new VerifiedIdentity(name, parsedBirthDate, parsedGender, parsedNationalInfo, mobileNo, parsedMobileCarrier, di);
    }
}
