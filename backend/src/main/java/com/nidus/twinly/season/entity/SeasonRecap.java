package com.nidus.twinly.season.entity;

import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "season_recaps")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonRecap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long seasonId;

    @JdbcTypeCode(SqlTypes.JSON)
    private String data;

    private Instant createdAt;

    public static SeasonRecap create(Long userId, Long seasonId, String data, Instant createdAt) {
        SeasonRecap recap = new SeasonRecap();

        recap.userId = userId;
        recap.seasonId = seasonId;
        recap.data = data;
        recap.createdAt = createdAt;

        return recap;
    }
}
