package com.nidus.twinly.anon.entity;

import com.nidus.twinly.common.crypto.EncryptedStringConverter;
import com.nidus.twinly.common.domain.Gender;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "anon_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnonSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID token;

    private Instant expiresAt;

    private String nickname;

    @Convert(converter = EncryptedStringConverter.class)
    private String familyName;

    @Convert(converter = EncryptedStringConverter.class)
    private String givenName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private Gender gender;

    @Convert(converter = EncryptedStringConverter.class)
    private String affiliation;

    @Convert(converter = EncryptedStringConverter.class)
    private String affiliationNumber;

    @Convert(converter = EncryptedStringConverter.class)
    private String birthDate;

    @Convert(converter = EncryptedStringConverter.class)
    private String height;

    @Convert(converter = EncryptedStringConverter.class)
    private String phoneNumber;

    private String phoneNumberHash;

    @Convert(converter = EncryptedStringConverter.class)
    private String email;

    private String emailHash;

    private Instant createdAt;

    public static AnonSession create(UUID token, Instant expiresAt) {
        AnonSession session = new AnonSession();
        session.token = token;
        session.expiresAt = expiresAt;
        session.createdAt = Instant.now();
        return session;
    }

    public void changeNickname(String nickname) { this.nickname = nickname; }
    public void changeFamilyName(String familyName) { this.familyName = familyName; }
    public void changeGivenName(String givenName) { this.givenName = givenName; }
    public void changeGender(Gender gender) { this.gender = gender; }
    public void changeAffiliation(String affiliation) { this.affiliation = affiliation; }
    public void changeAffiliationNumber(String affiliationNumber) { this.affiliationNumber = affiliationNumber; }
    public void changeBirthDate(String birthDate) { this.birthDate = birthDate; }
    public void changeHeight(String height) { this.height = height; }
    public void changePhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public void changePhoneNumberHash(String phoneNumberHash) { this.phoneNumberHash = phoneNumberHash; }
    public void changeEmail(String email) { this.email = email; }
    public void changeEmailHash(String emailHash) { this.emailHash = emailHash; }
}