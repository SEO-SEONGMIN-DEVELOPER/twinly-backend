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
@Table(name = "user_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @JdbcTypeCode(SqlTypes.JSON)
    private String room;

    private LocalDate date;

    private Instant createdAt;
}
