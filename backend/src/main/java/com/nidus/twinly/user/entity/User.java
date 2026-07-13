package com.nidus.twinly.user.entity;

import com.nidus.twinly.common.crypto.EncryptedStringConverter;
import com.nidus.twinly.common.domain.Gender;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    private Instant withdrawalRequestedAt;

    private Instant deletedAt;

    private Instant createdAt;

    public static User create(String nickname, String familyName, String givenName, Gender gender,
                              String affiliation, String affiliationNumber, String birthDate, String height,
                              String phoneNumber, String phoneNumberHash, String email, String emailHash) {
        User user = new User();

        user.nickname = nickname;
        user.familyName = familyName;
        user.givenName = givenName;
        user.gender = gender;
        user.affiliation = affiliation;
        user.affiliationNumber = affiliationNumber;
        user.birthDate = birthDate;
        user.height = height;
        user.phoneNumber = phoneNumber;
        user.phoneNumberHash = phoneNumberHash;
        user.email = email;
        user.emailHash = emailHash;
        user.createdAt = Instant.now();

        return user;
    }

    public void changeAffiliation(String affiliation) {
        this.affiliation = affiliation;
    }

    public void changeHeight(String height) {
        this.height = height;
    }

    public void requestWithdrawal() {
        this.withdrawalRequestedAt = Instant.now();
    }

    public void cancelWithdrawal() {
        this.withdrawalRequestedAt = null;
    }
}