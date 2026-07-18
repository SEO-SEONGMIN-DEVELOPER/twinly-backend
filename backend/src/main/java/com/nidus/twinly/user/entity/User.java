package com.nidus.twinly.user.entity;

import org.hibernate.annotations.DynamicUpdate;
import com.nidus.twinly.common.crypto.EncryptedStringConverter;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.user.domain.AvatarPaletteColor;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nickname;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private AvatarPaletteColor avatarPaletteColor;

    @Convert(converter = EncryptedStringConverter.class)
    private String familyName;

    private String familyNameHash;

    @Convert(converter = EncryptedStringConverter.class)
    private String givenName;

    private String givenNameHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private Gender gender;

    @Convert(converter = EncryptedStringConverter.class)
    private String affiliation;

    private String affiliationHash;

    @Convert(converter = EncryptedStringConverter.class)
    private String affiliationNumber;

    private String affiliationNumberHash;

    @Convert(converter = EncryptedStringConverter.class)
    private String birthDate;

    private String birthDateHash;

    @Convert(converter = EncryptedStringConverter.class)
    private String phoneNumber;

    private String phoneNumberHash;

    @Convert(converter = EncryptedStringConverter.class)
    private String email;

    private String emailHash;

    private Instant withdrawalRequestedAt;

    private Instant withdrawalScheduledAt;

    private Instant deletedAt;

    private Instant createdAt;

    public static User create(String nickname,
                              String familyName, String familyNameHash,
                              String givenName, String givenNameHash,
                              Gender gender,
                              String affiliation, String affiliationHash,
                              String affiliationNumber, String affiliationNumberHash,
                              String birthDate, String birthDateHash,
                              String phoneNumber, String phoneNumberHash,
                              String email, String emailHash) {
        User user = new User();

        user.nickname = nickname;
        user.familyName = familyName;
        user.familyNameHash = familyNameHash;
        user.givenName = givenName;
        user.givenNameHash = givenNameHash;
        user.gender = gender;
        user.affiliation = affiliation;
        user.affiliationHash = affiliationHash;
        user.affiliationNumber = affiliationNumber;
        user.affiliationNumberHash = affiliationNumberHash;
        user.birthDate = birthDate;
        user.birthDateHash = birthDateHash;
        user.phoneNumber = phoneNumber;
        user.phoneNumberHash = phoneNumberHash;
        user.email = email;
        user.emailHash = emailHash;
        user.createdAt = Instant.now();

        return user;
    }

    public void changeAffiliation(String affiliation, String affiliationHash) {
        this.affiliation = affiliation;
        this.affiliationHash = affiliationHash;
    }

    public void requestWithdrawal(Duration gracePeriod) {
        this.withdrawalRequestedAt = Instant.now();
        this.withdrawalScheduledAt = this.withdrawalRequestedAt.plus(gracePeriod);

    }

    public void cancelWithdrawal() {
        this.withdrawalRequestedAt = null;
    }
}