package com.nidus.twinly.user.entity;

import org.hibernate.annotations.DynamicUpdate;
import com.nidus.twinly.user.domain.DisclosureField;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "disclosure_agreements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DisclosureAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private DisclosureField field;

    private Instant agreedAt;

    private Instant createdAt;

    public static DisclosureAgreement create(Long userId, DisclosureField field) {
        DisclosureAgreement agreement = new DisclosureAgreement();
        Instant now = Instant.now();

        agreement.userId = userId;
        agreement.field = field;
        agreement.agreedAt = now;
        agreement.createdAt = now;

        return agreement;
    }
}