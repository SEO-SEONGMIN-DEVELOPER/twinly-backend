package com.nidus.twinly.showcase.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@DynamicUpdate
@Table(name = "showcases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Showcase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long viewerUserId;

    private Long targetUserId;

    private LocalDate date;

    private Instant createdAt;

    public static Showcase create(Long viewerUserId, Long targetUserId, LocalDate date) {
        Showcase showcase = new Showcase();

        showcase.viewerUserId = viewerUserId;
        showcase.targetUserId = targetUserId;
        showcase.date = date;
        showcase.createdAt = Instant.now();

        return showcase;
    }
}
