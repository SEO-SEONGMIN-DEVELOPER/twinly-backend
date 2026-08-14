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

    public static Scene createAction(Long userId, LocalDate date, String version, String place,
                                     LocalDateTime startsAt, LocalDateTime endsAt, String narration, String mind) {
        Scene scene = newScene(userId, date, version, place, startsAt, endsAt, SceneType.ACTION);

        scene.narration = narration;
        scene.mind = mind;

        return scene;
    }

    public static Scene createDialogue(Long userId, LocalDate date, String version, String place,
                                       LocalDateTime startsAt, LocalDateTime endsAt, String lines) {
        Scene scene = newScene(userId, date, version, place, startsAt, endsAt, SceneType.DIALOGUE);

        scene.lines = lines;

        return scene;
    }

    private static Scene newScene(Long userId, LocalDate date, String version, String place,
                                  LocalDateTime startsAt, LocalDateTime endsAt, SceneType type) {
        Scene scene = new Scene();

        scene.userId = userId;
        scene.date = date;
        scene.version = version;
        scene.place = place;
        scene.startsAt = startsAt;
        scene.endsAt = endsAt;
        scene.type = type;
        scene.createdAt = Instant.now();

        return scene;
    }
}