package com.nidus.twinly.balancegame.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "balance_game_partner_answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BalanceGameAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long roundId;

    private Long userId;

    private Long partnerUserId;

    private Long optionId;

    private Instant createdAt;

    public static BalanceGameAnswer create(Long roundId, Long userId, Long partnerUserId, Long optionId) {
        BalanceGameAnswer answer = new BalanceGameAnswer();

        answer.roundId = roundId;
        answer.userId = userId;
        answer.partnerUserId = partnerUserId;
        answer.optionId = optionId;
        answer.createdAt = Instant.now();

        return answer;
    }
}
