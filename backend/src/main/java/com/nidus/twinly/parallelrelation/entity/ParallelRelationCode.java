package com.nidus.twinly.parallelrelation.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "parallel_relation_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParallelRelationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String code;

    private Instant createdAt;

    public static ParallelRelationCode create(Long userId, String code) {
        ParallelRelationCode parallelRelationCode = new ParallelRelationCode();

        parallelRelationCode.userId = userId;
        parallelRelationCode.code = code;
        parallelRelationCode.createdAt = Instant.now();

        return parallelRelationCode;
    }
}
