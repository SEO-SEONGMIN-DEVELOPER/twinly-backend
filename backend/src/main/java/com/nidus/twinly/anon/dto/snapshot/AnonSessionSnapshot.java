package com.nidus.twinly.anon.dto.snapshot;

import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.common.domain.Gender;

import java.time.Instant;
import java.util.UUID;

/*
 * [멘토링 피드백 반영 완료]
 * Controller -> Service 에서 Snapshot 전달
 */

public record AnonSessionSnapshot(
        Long id,
        UUID token,
        Instant expiresAt,
        String nickname,
        String familyName,
        String givenName,
        Gender gender,
        String affiliation,
        String affiliationNumber,
        String birthDate,
        String phoneNumber,
        String phoneNumberHash,
        String email,
        String emailHash,
        Instant createdAt
) {
    public static AnonSessionSnapshot from(AnonSession anonSession) {
        return new AnonSessionSnapshot(
                anonSession.getId(),
                anonSession.getToken(),
                anonSession.getExpiresAt(),
                anonSession.getNickname(),
                anonSession.getFamilyName(),
                anonSession.getGivenName(),
                anonSession.getGender(),
                anonSession.getAffiliation(),
                anonSession.getAffiliationNumber(),
                anonSession.getBirthDate(),
                anonSession.getPhoneNumber(),
                anonSession.getPhoneNumberHash(),
                anonSession.getEmail(),
                anonSession.getEmailHash(),
                anonSession.getCreatedAt()
        );
    }
}
