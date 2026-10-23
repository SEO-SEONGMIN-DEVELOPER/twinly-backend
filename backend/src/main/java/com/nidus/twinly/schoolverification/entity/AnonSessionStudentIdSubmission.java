package com.nidus.twinly.schoolverification.entity;

import com.nidus.twinly.common.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

@Entity
@DynamicUpdate
@Table(name = "anon_session_student_id_submissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnonSessionStudentIdSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID submissionId;

    private Long anonSessionId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String organization;

    private String uploadKey;

    private String reviewKey;

    private Instant submittedAt;

    private Instant replacedAt;

    private Instant photoDeletedAt;

    private Instant createdAt;

    public static AnonSessionStudentIdSubmission create(UUID submissionId, Long anonSessionId, String organization,
                                                        String uploadKey, String reviewKey) {
        AnonSessionStudentIdSubmission submission = new AnonSessionStudentIdSubmission();
        Instant now = Instant.now();

        submission.submissionId = submissionId;
        submission.anonSessionId = anonSessionId;
        submission.organization = organization;
        submission.uploadKey = uploadKey;
        submission.reviewKey = reviewKey;
        submission.submittedAt = now;
        submission.createdAt = now;

        return submission;
    }

    public boolean isCurrent() {
        return replacedAt == null;
    }

    public void changeOrganization(String organization) {
        this.organization = organization;
    }

    public void replace() {
        this.replacedAt = Instant.now();
    }

    public void markPhotoDeleted() {
        this.photoDeletedAt = Instant.now();
    }
}
