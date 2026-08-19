package com.nidus.twinly.parallelrelation.repository;

import com.nidus.twinly.parallelrelation.entity.ParallelRelationCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ParallelRelationCodeRepository extends JpaRepository<ParallelRelationCode, Long> {

    Optional<ParallelRelationCode> findByUserId(Long userId);

    Optional<ParallelRelationCode> findByCode(String code);

    boolean existsByCode(String code);
}
