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

    @Column(columnDefinition = "TEXT")
    private String version;

    @Column(columnDefinition = "TEXT")
    private String place;

    private LocalDateTime startsAt;

    private LocalDateTime endsAt;

    @Enumerated(EnumType.STRING)
    private SceneType type;

    @Column(columnDefinition = "TEXT")
    private String narration;

    @Column(columnDefinition = "TEXT")
    private String mind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "`lines`")
    private String lines;

    private Instant createdAt;
}