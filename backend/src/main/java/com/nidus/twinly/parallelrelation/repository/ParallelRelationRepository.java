package com.nidus.twinly.parallelrelation.repository;

import com.nidus.twinly.parallelrelation.entity.ParallelRelation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParallelRelationRepository extends JpaRepository<ParallelRelation, Long> {

    Optional<ParallelRelation> findByUserAIdAndUserBId(Long userAId, Long userBId);

    List<ParallelRelation> findAllByUserAIdOrUserBIdOrderByIdDesc(Long userAId, Long userBId);
}
