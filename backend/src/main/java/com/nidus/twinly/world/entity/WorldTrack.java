package com.nidus.twinly.world.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "world_tracks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldTrack {

    public static final long PUBLIC_VIEWER_USER_ID = 0L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer poolNumber;

    private LocalDate date;

    private String sectorCode;

    private Long viewerUserId;

    @Column(columnDefinition = "LONGBLOB")
    private byte[] body;

    private Instant receivedAt;

    public static long toViewerUserId(Long viewerUserId) {
        return viewerUserId == null ? PUBLIC_VIEWER_USER_ID : viewerUserId;
    }
}
