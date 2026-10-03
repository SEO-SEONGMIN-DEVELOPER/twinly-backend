package com.nidus.twinly.relationship.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "intimacy_bonuses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IntimacyBonus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_a_id")
    private Long userAId;

    @Column(name = "user_b_id")
    private Long userBId;

    private Integer amount;

    private Instant createdAt;

    public static IntimacyBonus create(Long userId1, Long userId2, int amount) {
        IntimacyBonus bonus = new IntimacyBonus();

        bonus.userAId = Math.min(userId1, userId2);
        bonus.userBId = Math.max(userId1, userId2);
        bonus.amount = amount;
        bonus.createdAt = Instant.now();

        return bonus;
    }
}
