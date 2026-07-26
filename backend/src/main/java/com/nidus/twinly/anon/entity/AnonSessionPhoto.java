package com.nidus.twinly.anon.entity;

import org.hibernate.annotations.DynamicUpdate;
import com.nidus.twinly.common.photo.PhotoType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "anon_session_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnonSessionPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long anonSessionId;

    @Enumerated(EnumType.STRING)
    private PhotoType type;

    @Column(columnDefinition = "TEXT")
    private String key;

    private Integer xPos;

    private Integer yPos;

    private Integer width;

    private Integer height;

    private Instant uploadedAt;

    private Instant createdAt;

    public static AnonSessionPhoto create(Long anonSessionId, PhotoType type, String key, Integer xPos, Integer yPos, Integer width, Integer height) {
        AnonSessionPhoto photo = new AnonSessionPhoto();

        photo.anonSessionId = anonSessionId;
        photo.type = type;
        photo.key = key;
        photo.xPos = xPos;
        photo.yPos = yPos;
        photo.width = width;
        photo.height = height;
        photo.uploadedAt = Instant.now();
        photo.createdAt = Instant.now();

        return photo;
    }

    public void changePhoto(String key, Integer xPos, Integer yPos, Integer width, Integer height) {
        this.key = key;
        this.xPos = xPos;
        this.yPos = yPos;
        this.width = width;
        this.height = height;
        this.uploadedAt = Instant.now();
    }
}