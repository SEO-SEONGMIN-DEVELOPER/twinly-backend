package com.nidus.twinly.schoolverification.repository;

import com.nidus.twinly.schoolverification.domain.StudentIdSubmissionStatus;
import com.nidus.twinly.schoolverification.entity.StudentIdSubmission;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentIdSubmissionRepository extends JpaRepository<StudentIdSubmission, Long> {

    Optional<StudentIdSubmission> findBySubmissionId(UUID submissionId);

    Optional<StudentIdSubmission> findFirstByUserIdOrderByIdDesc(Long userId);

    List<StudentIdSubmission> findAllByUserIdOrderByIdDesc(Long userId);

    boolean existsByUploadKey(String uploadKey);

    @Query(value = """
            SELECT s FROM StudentIdSubmission s, User u
            WHERE u.id = s.userId
              AND s.status <> com.nidus.twinly.schoolverification.domain.StudentIdSubmissionStatus.REPLACED
              AND (:status IS NULL OR s.status = :status)
              AND (s.status <> com.nidus.twinly.schoolverification.domain.StudentIdSubmissionStatus.PENDING
                   OR (u.withdrawalRequestedAt IS NULL AND u.deletedAt IS NULL))
            ORDER BY CASE WHEN s.status = com.nidus.twinly.schoolverification.domain.StudentIdSubmissionStatus.PENDING THEN 0 ELSE 1 END,
                     CASE WHEN s.status = com.nidus.twinly.schoolverification.domain.StudentIdSubmissionStatus.PENDING THEN s.submittedAt END ASC,
                     s.submittedAt DESC, s.id DESC
            """,
            countQuery = """
            SELECT COUNT(s) FROM StudentIdSubmission s, User u
            WHERE u.id = s.userId
              AND s.status <> com.nidus.twinly.schoolverification.domain.StudentIdSubmissionStatus.REPLACED
              AND (:status IS NULL OR s.status = :status)
              AND (s.status <> com.nidus.twinly.schoolverification.domain.StudentIdSubmissionStatus.PENDING
                   OR (u.withdrawalRequestedAt IS NULL AND u.deletedAt IS NULL))
            """)
    Page<StudentIdSubmission> findForAdmin(@Param("status") StudentIdSubmissionStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StudentIdSubmission> findWithLockBySubmissionId(UUID submissionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE StudentIdSubmission s
            SET s.status = com.nidus.twinly.schoolverification.domain.StudentIdSubmissionStatus.CANCELED,
                s.decidedAt = :canceledAt
            WHERE s.userId IN :userIds
              AND s.status = com.nidus.twinly.schoolverification.domain.StudentIdSubmissionStatus.PENDING
            """)
    int cancelPendingByUserIdIn(@Param("userIds") Collection<Long> userIds, @Param("canceledAt") Instant canceledAt);

    @Query("""
            SELECT s FROM StudentIdSubmission s
            WHERE s.photoDeletedAt IS NULL
              AND s.status <> com.nidus.twinly.schoolverification.domain.StudentIdSubmissionStatus.PENDING
            ORDER BY s.id ASC
            """)
    List<StudentIdSubmission> findPhotoPurgeTargets(Pageable pageable);

    @Transactional
    @Modifying
    @Query("""
            UPDATE StudentIdSubmission s
            SET s.photoDeletedAt = :deletedAt
            WHERE s.id = :id
              AND s.photoDeletedAt IS NULL
            """)
    int markPhotoDeleted(@Param("id") Long id, @Param("deletedAt") Instant deletedAt);
}
