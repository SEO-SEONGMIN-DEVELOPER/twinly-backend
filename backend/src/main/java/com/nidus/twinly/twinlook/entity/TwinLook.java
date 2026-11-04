package com.nidus.twinly.twinlook.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "twin_looks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TwinLook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Integer hair;

    private Integer hairColor;

    private Integer outfit;

    private Integer outfitColor;

    private Instant createdAt;

    private Instant updatedAt;
}
