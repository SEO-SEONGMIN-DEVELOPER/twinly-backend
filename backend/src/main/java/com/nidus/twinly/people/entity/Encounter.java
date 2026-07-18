package com.nidus.twinly.people.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "encounters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Encounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userAId;

    private Long userBId;

    private Instant createdAt;

    public static Encounter create(Long userId1, Long userId2) {
        Encounter encounter = new Encounter();
        encounter.userAId = Math.min(userId1, userId2);
        encounter.userBId = Math.max(userId1, userId2);
        encounter.createdAt = Instant.now();
        return encounter;
    }
}
