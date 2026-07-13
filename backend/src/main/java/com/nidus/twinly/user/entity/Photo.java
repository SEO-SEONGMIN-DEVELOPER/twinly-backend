package com.nidus.twinly.user.entity;

import com.nidus.twinly.common.photo.PhotoType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private PhotoType type;

    private String key;

    private Integer xPos;

    private Integer yPos;

    private Integer width;

    private Integer height;

    private Instant uploadedAt;

    private Instant createdAt;

    public static Photo create(Long userId, PhotoType type, String key, Integer xPos, Integer yPos, Integer width, Integer height, Instant uploadedAt) {
        Photo photo = new Photo();

        photo.userId = userId;
        photo.type = type;
        photo.key = key;
        photo.xPos = xPos;
        photo.yPos = yPos;
        photo.width = width;
        photo.height = height;
        photo.uploadedAt = uploadedAt;
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