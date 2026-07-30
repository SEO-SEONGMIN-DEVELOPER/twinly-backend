package com.nidus.twinly.user.entity;

import org.hibernate.annotations.DynamicUpdate;
import com.nidus.twinly.common.crypto.EncryptedStringConverter;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.user.domain.AvatarPaletteColor;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    public static final String WITHDRAWN_NAME = "탈퇴한 사용자";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String nickname;

    @Enumerated(EnumType.STRING)
    private AvatarPaletteColor avatarPaletteColor;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String familyName;

    @Column(columnDefinition = "TEXT")
    private String familyNameHash;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String givenName;

    @Column(columnDefinition = "TEXT")
    private String givenNameHash;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String affiliation;

    @Column(columnDefinition = "TEXT")
    private String affiliationHash;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String affiliationNumber;

    @Column(columnDefinition = "TEXT")
    private String affiliationNumberHash;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String birthDate;

    @Column(columnDefinition = "TEXT")
    private String birthDateHash;

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

    public boolean isWithdrawn() {
        return deletedAt != null;
    }

    public String displayName() {
        return isWithdrawn() ? WITHDRAWN_NAME : familyName + givenName;
    }

    public String displayGivenName() {
        return isWithdrawn() ? WITHDRAWN_NAME : givenName;
    }

    public String displayNickname() {
        return isWithdrawn() ? WITHDRAWN_NAME : nickname;
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
        this.withdrawalScheduledAt = null;
    }
}