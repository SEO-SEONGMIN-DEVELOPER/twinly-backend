package com.nidus.twinly.schoolverification.repository;

import com.nidus.twinly.schoolverification.entity.AnonSessionStudentIdSubmission;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AnonSessionStudentIdSubmissionRepository extends JpaRepository<AnonSessionStudentIdSubmission, Long> {

    Optional<AnonSessionStudentIdSubmission> findByAnonSessionIdAndReplacedAtIsNull(Long anonSessionId);

    List<AnonSessionStudentIdSubmission> findAllByAnonSessionIdOrderByIdAsc(Long anonSessionId);

    boolean existsByUploadKey(String uploadKey);

    @Query("""
            SELECT s FROM AnonSessionStudentIdSubmission s
            WHERE s.photoDeletedAt IS NULL
              AND s.replacedAt IS NOT NULL
            ORDER BY s.id ASC
            """)
    List<AnonSessionStudentIdSubmission> findPhotoPurgeTargets(Pageable pageable);

    @Transactional
    @Modifying
    @Query("""
            UPDATE AnonSessionStudentIdSubmission s
            SET s.photoDeletedAt = :deletedAt
            WHERE s.id = :id
              AND s.photoDeletedAt IS NULL
            """)
    int markPhotoDeleted(@Param("id") Long id, @Param("deletedAt") Instant deletedAt);
}
