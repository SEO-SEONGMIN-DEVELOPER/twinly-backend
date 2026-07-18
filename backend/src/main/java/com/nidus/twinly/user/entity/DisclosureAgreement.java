package com.nidus.twinly.user.entity;

import org.hibernate.annotations.DynamicUpdate;
import com.nidus.twinly.user.domain.DisclosureField;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private DisclosureField field;

    private Instant agreedAt;

    private Instant createdAt;

    public static DisclosureAgreement create(Long userId, DisclosureField field) {
        DisclosureAgreement agreement = new DisclosureAgreement();

        agreement.userId = userId;
        agreement.field = field;
        agreement.agreedAt = Instant.now();
        agreement.createdAt = Instant.now();

        return agreement;
    }
}