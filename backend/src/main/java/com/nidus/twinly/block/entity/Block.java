package com.nidus.twinly.block.entity;

import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "blocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long blockedUserId;

    private Instant createdAt;

    public static Block create(Long userId, Long blockedUserId) {
        Block block = new Block();

        block.userId = userId;
        block.blockedUserId = blockedUserId;
        block.createdAt = Instant.now();

        return block;
    }
}