package com.nidus.twinly.anon.entity;

import com.nidus.twinly.common.domain.Gender;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("anon_sessions")
public record AnonSession(@Id Long id,
                          UUID token,
                          Instant expiresAt,
                          String nickname,
                          String familyName,
                          String givenName,
                          Gender gender,
                          String affiliation,
                          String affiliationNumber,
                          String experience,
                          String birthDate,
                          String height,
                          String phoneNumber,
                          String email) {

    public static AnonSession create(UUID token, Instant expiresAt) {
        return new AnonSession(null, token, expiresAt, null, null, null, null, null, null, null, null, null, null, null);
    }

    public AnonSession withNickname(String nickname) {
        return new AnonSession(id, token, expiresAt, nickname, familyName, givenName,
                gender, affiliation, affiliationNumber, experience, birthDate, height,
                phoneNumber, email);
    }

    public AnonSession withFamilyName(String familyName) {
        return new AnonSession(id, token, expiresAt, nickname, familyName, givenName,
                gender, affiliation, affiliationNumber, experience, birthDate, height,
                phoneNumber, email);
    }

    public AnonSession withGivenName(String givenName) {
        return new AnonSession(id, token, expiresAt, nickname, familyName, givenName,
                gender, affiliation, affiliationNumber, experience, birthDate, height,
                phoneNumber, email);
    }

    public AnonSession withGender(Gender gender) {
        return new AnonSession(id, token, expiresAt, nickname, familyName, givenName,
                gender, affiliation, affiliationNumber, experience, birthDate, height,
                phoneNumber, email);
    }

    public AnonSession withAffiliation(String affiliation) {
        return new AnonSession(id, token, expiresAt, nickname, familyName, givenName,
                gender, affiliation, affiliationNumber, experience, birthDate, height,
                phoneNumber, email);
    }

    public AnonSession withAffiliationNumber(String affiliationNumber) {
        return new AnonSession(id, token, expiresAt, nickname, familyName, givenName,
                gender, affiliation, affiliationNumber, experience, birthDate, height,
                phoneNumber, email);
    }

    public AnonSession withExperience(String experience) {
        return new AnonSession(id, token, expiresAt, nickname, familyName, givenName,
                gender, affiliation, affiliationNumber, experience, birthDate, height,
                phoneNumber, email);
    }

    public AnonSession withBirthDate(String birthDate) {
        return new AnonSession(id, token, expiresAt, nickname, familyName, givenName,
                gender, affiliation, affiliationNumber, experience, birthDate, height,
                phoneNumber, email);
    }

    public AnonSession withHeight(String height) {
        return new AnonSession(id, token, expiresAt, nickname, familyName, givenName,
                gender, affiliation, affiliationNumber, experience, birthDate, height,
                phoneNumber, email);
    }

    public AnonSession withPhoneNumber(String phoneNumber) {
        return new AnonSession(id, token, expiresAt, nickname, familyName, givenName,
                gender, affiliation, affiliationNumber, experience, birthDate, height,
                phoneNumber, email);
    }

    public AnonSession withEmail(String email) {
        return new AnonSession(id, token, expiresAt, nickname, familyName, givenName,
                gender, affiliation, affiliationNumber, experience, birthDate, height,
                phoneNumber, email);
    }
}