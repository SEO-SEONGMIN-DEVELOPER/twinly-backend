package com.nidus.twinly.activity.entity;

import org.hibernate.annotations.DynamicUpdate;
import com.nidus.twinly.activity.domain.SceneType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@DynamicUpdate
@Table(name = "scenes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Scene {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private LocalDate date;

    private String version;

    private String place;

    private LocalDateTime startsAt;

    private LocalDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private SceneType type;

    private String narration;

    private String mind;

    @JdbcTypeCode(SqlTypes.JSON)
    private String dialogues;

    private Instant createdAt;
}