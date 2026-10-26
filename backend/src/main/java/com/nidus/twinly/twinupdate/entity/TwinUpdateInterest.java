package com.nidus.twinly.twinupdate.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "twin_update_interests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TwinUpdateInterest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long twinUpdateId;

    private String interest;

    private Instant createdAt;

    public static TwinUpdateInterest create(Long twinUpdateId, String interest, Instant createdAt) {
        TwinUpdateInterest twinUpdateInterest = new TwinUpdateInterest();
        twinUpdateInterest.twinUpdateId = twinUpdateId;
        twinUpdateInterest.interest = interest;
        twinUpdateInterest.createdAt = createdAt;

        return twinUpdateInterest;
    }
}
