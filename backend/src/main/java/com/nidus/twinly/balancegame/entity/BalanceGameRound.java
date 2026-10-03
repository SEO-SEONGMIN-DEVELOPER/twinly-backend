package com.nidus.twinly.balancegame.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Duration;
import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "balance_game_rounds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BalanceGameRound {

    private static final Duration DURATION = Duration.ofHours(1);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant startsAt;

    private Long questionId;

    private Instant summarySentAt;

    private Instant createdAt;

    public Instant endsAt() {
        return startsAt.plus(DURATION);
    }

    public boolean isEnded(Instant now) {
        return !now.isBefore(endsAt());
    }
}
