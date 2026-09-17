package com.nidus.twinly.user.seed.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "seed_resource_hashes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeedResourceHash {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String resource;

    @Column(columnDefinition = "CHAR(64)")
    private String hash;

    private Instant updatedAt;

    private Instant createdAt;

    public static SeedResourceHash create(String resource, String hash, Instant now) {
        SeedResourceHash created = new SeedResourceHash();
        created.resource = resource;
        created.hash = hash;
        created.updatedAt = now;
        created.createdAt = now;

        return created;
    }

    public void change(String hash, Instant now) {
        this.hash = hash;
        this.updatedAt = now;
    }
}
