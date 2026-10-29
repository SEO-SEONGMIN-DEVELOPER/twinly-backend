package com.nidus.twinly.activity.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@DynamicUpdate
@Table(name = "simulation_home_objects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SimulationHomeObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private LocalDate date;

    @JdbcTypeCode(SqlTypes.JSON)
    private String objects;

    private Instant createdAt;

    public static SimulationHomeObject create(Long userId, LocalDate date, String objects) {
        SimulationHomeObject homeObject = new SimulationHomeObject();

        homeObject.userId = userId;
        homeObject.date = date;
        homeObject.objects = objects;
        homeObject.createdAt = Instant.now();

        return homeObject;
    }
}
