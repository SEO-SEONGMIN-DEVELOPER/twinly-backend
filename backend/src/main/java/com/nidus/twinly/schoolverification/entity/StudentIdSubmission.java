package com.nidus.twinly.schoolverification.entity;

import com.nidus.twinly.common.crypto.EncryptedStringConverter;
import com.nidus.twinly.schoolverification.domain.StudentIdRejectReason;
import com.nidus.twinly.schoolverification.domain.StudentIdSubmissionStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

@Entity
@DynamicUpdate
@Table(name = "student_id_submissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentIdSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID submissionId;

    private Long userId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String organization;

    private String uploadKey;

    private String reviewKey;

    @Enumerated(EnumType.STRING)
    private StudentIdSubmissionStatus status;

    @Enumerated(EnumType.STRING)
    private StudentIdRejectReason rejectReason;

    private String reviewer;

    private Instant submittedAt;

    private Instant decidedAt;

    private Instant photoDeletedAt;

    private Instant createdAt;

    public static StudentIdSubmission create(UUID submissionId, Long userId, String organization,
                                             String uploadKey, String reviewKey) {
        StudentIdSubmission submission = new StudentIdSubmission();
        Instant now = Instant.now();

        submission.submissionId = submissionId;
        submission.userId = userId;
        submission.organization = organization;
        submission.uploadKey = uploadKey;
        submission.reviewKey = reviewKey;
        submission.status = StudentIdSubmissionStatus.PENDING;
        submission.submittedAt = now;
        submission.createdAt = now;

        return submission;
    }

    public static StudentIdSubmission transfer(AnonSessionStudentIdSubmission anonSubmission, Long userId) {
        StudentIdSubmission submission = new StudentIdSubmission();

        submission.submissionId = anonSubmission.getSubmissionId();
        submission.userId = userId;
        submission.organization = anonSubmission.getOrganization();
        submission.uploadKey = anonSubmission.getUploadKey();
        submission.reviewKey = anonSubmission.getReviewKey();
        submission.status = anonSubmission.isCurrent() ? StudentIdSubmissionStatus.PENDING : StudentIdSubmissionStatus.REPLACED;
        submission.submittedAt = anonSubmission.getSubmittedAt();
        submission.photoDeletedAt = anonSubmission.getPhotoDeletedAt();
        submission.createdAt = Instant.now();

        return submission;
    }

    public boolean isPending() {
        return status == StudentIdSubmissionStatus.PENDING;
    }

    public void approve(String reviewer) {
        this.status = StudentIdSubmissionStatus.APPROVED;
        this.reviewer = reviewer;
        this.decidedAt = Instant.now();
    }

    public void reject(StudentIdRejectReason rejectReason, String reviewer) {
        this.status = StudentIdSubmissionStatus.REJECTED;
        this.rejectReason = rejectReason;
        this.reviewer = reviewer;
        this.decidedAt = Instant.now();
    }

    public boolean isPhotoDeleted() {
        return photoDeletedAt != null;
    }
}
