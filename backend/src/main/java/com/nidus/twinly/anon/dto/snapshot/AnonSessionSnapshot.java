package com.nidus.twinly.anon.dto.snapshot;

import com.nidus.twinly.anon.entity.AnonSession;

import java.time.Instant;
import java.util.UUID;

public record AnonSessionSnapshot(
        Long id,
        UUID token,
        Instant expiresAt,
        String nickname,
        String familyName,
        String givenName,
        String affiliation,
        String affiliationNumber,
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
                anonSession.getAffiliation(),
                anonSession.getAffiliationNumber(),
                anonSession.getPhoneNumber(),
                anonSession.getPhoneNumberHash(),
                anonSession.getEmail(),
                anonSession.getEmailHash(),
                anonSession.getCreatedAt()
        );
    }
}
