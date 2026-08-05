package com.nidus.twinly.anon.entity;

import org.hibernate.annotations.DynamicUpdate;
import com.nidus.twinly.common.crypto.EncryptedStringConverter;
import com.nidus.twinly.common.domain.Gender;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@DynamicUpdate
@Table(name = "anon_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnonSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID token;

    private Instant expiresAt;

    @Column(columnDefinition = "TEXT")
    private String nickname;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String familyName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String givenName;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String school;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String affiliation;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String affiliationNumber;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String birthDate;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String phoneNumber;

    @Column(columnDefinition = "TEXT")
    private String phoneNumberHash;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String email;

    @Column(columnDefinition = "TEXT")
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
    public void changeSchool(String school) { this.school = school; }
    public void changeAffiliation(String affiliation) { this.affiliation = affiliation; }
    public void changeAffiliationNumber(String affiliationNumber) { this.affiliationNumber = affiliationNumber; }
    public void changeBirthDate(String birthDate) { this.birthDate = birthDate; }
    public void changePhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public void changePhoneNumberHash(String phoneNumberHash) { this.phoneNumberHash = phoneNumberHash; }
    public void changeEmail(String email) { this.email = email; }
    public void changeEmailHash(String emailHash) { this.emailHash = emailHash; }
}