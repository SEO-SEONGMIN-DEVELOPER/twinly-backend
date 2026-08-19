package com.nidus.twinly.parallelrelation.entity;

import com.nidus.twinly.common.parallel.ParallelRelationType;
import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "parallel_relations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParallelRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_a_id")
    private Long userAId;

    @Column(name = "user_b_id")
    private Long userBId;

    private Long codeOwnerId;

    private Integer similarity;

    @Enumerated(EnumType.STRING)
    private ParallelRelationType relation;

    private Integer storyIndex;

    private Instant createdAt;

    public static ParallelRelation create(Long codeOwnerId, Long submitterId, Integer similarity, ParallelRelationType relation, Integer storyIndex) {
        ParallelRelation pair = new ParallelRelation();

        pair.userAId = Math.min(codeOwnerId, submitterId);
        pair.userBId = Math.max(codeOwnerId, submitterId);
        pair.codeOwnerId = codeOwnerId;
        pair.similarity = similarity;
        pair.relation = relation;
        pair.storyIndex = storyIndex;
        pair.createdAt = Instant.now();

        return pair;
    }

    public boolean hasParticipant(Long userId) {
        return userAId.equals(userId) || userBId.equals(userId);
    }

    public Long partnerIdOf(Long userId) {
        return userAId.equals(userId) ? userBId : userAId;
    }

    public Long submitterId() {
        return codeOwnerId.equals(userAId) ? userBId : userAId;
    }
}
