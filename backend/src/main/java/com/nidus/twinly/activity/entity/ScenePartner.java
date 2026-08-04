package com.nidus.twinly.activity.entity;

import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "scene_partners")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScenePartner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sceneId;

    private Long userId;

    private Instant createdAt;

    public static ScenePartner create(Long sceneId, Long userId) {
        ScenePartner scenePartner = new ScenePartner();

        scenePartner.sceneId = sceneId;
        scenePartner.userId = userId;
        scenePartner.createdAt = Instant.now();

        return scenePartner;
    }
}