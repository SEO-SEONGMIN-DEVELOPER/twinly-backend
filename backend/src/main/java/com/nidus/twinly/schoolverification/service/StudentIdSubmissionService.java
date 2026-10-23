package com.nidus.twinly.schoolverification.service;

import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.auth.repository.AnonSessionVerificationSessionRepository;
import com.nidus.twinly.common.domain.VerificationType;
import com.nidus.twinly.common.presign.PhotoPresignResult;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.organization.entity.Organization;
import com.nidus.twinly.organization.service.OrganizationCatalog;
import com.nidus.twinly.schoolverification.domain.SchoolVerificationStatus;
import com.nidus.twinly.schoolverification.entity.AnonSessionStudentIdSubmission;
import com.nidus.twinly.schoolverification.entity.StudentIdSubmission;
import com.nidus.twinly.schoolverification.event.StudentIdReviewRequestedEvent;
import com.nidus.twinly.schoolverification.repository.AnonSessionStudentIdSubmissionRepository;
import com.nidus.twinly.schoolverification.repository.StudentIdSubmissionRepository;
import com.nidus.twinly.schoolverification.storage.StudentIdPhotoOwner;
import com.nidus.twinly.schoolverification.storage.StudentIdPhotoStorage;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentIdSubmissionService {

    private final OrganizationCatalog organizationCatalog;
    private final StudentIdPhotoStorage studentIdPhotoStorage;
    private final ApplicationEventPublisher eventPublisher;

    private final AnonSessionRepository anonSessionRepository;
    private final AnonSessionVerificationSessionRepository anonSessionVerificationSessionRepository;
    private final AnonSessionStudentIdSubmissionRepository anonSessionStudentIdSubmissionRepository;
    private final StudentIdSubmissionRepository studentIdSubmissionRepository;
    private final UserRepository userRepository;

    public PhotoPresignResult presignForAnonSession(Long anonSessionId, String contentType) {
        return studentIdPhotoStorage.presign(StudentIdPhotoOwner.anonSession(anonSessionId), contentType);
    }

    @Transactional
    public void submitForAnonSession(Long anonSessionId, String organizationName, String key) {
        AnonSession anonSession = anonSessionRepository.findWithLockById(anonSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ANON_SESSION));

        Organization organization = organizationCatalog.findByName(organizationName);

        AnonSessionStudentIdSubmission current = anonSessionStudentIdSubmissionRepository
                .findByAnonSessionIdAndReplacedAtIsNull(anonSessionId)
                .orElse(null);

        if (current != null && current.getUploadKey().equals(key)) {
            current.changeOrganization(organization.getName());
            anonSession.changeOrganization(organization.getName());
            return;
        }

        requireUnusedKey(anonSessionStudentIdSubmissionRepository.existsByUploadKey(key), key);
        studentIdPhotoStorage.requireUploaded(StudentIdPhotoOwner.anonSession(anonSessionId), key);

        UUID submissionId = UUID.randomUUID();
        String reviewKey = studentIdPhotoStorage.copyToReview(key, submissionId);

        if (current != null) {
            current.replace();
        }

        anonSessionStudentIdSubmissionRepository.save(AnonSessionStudentIdSubmission.create(
                submissionId, anonSessionId, organization.getName(), key, reviewKey));

        anonSession.changeOrganization(organization.getName());
        anonSessionVerificationSessionRepository.deleteByAnonSessionIdAndType(anonSessionId, VerificationType.EMAIL);
    }

    public Optional<AnonSessionStudentIdSubmission> currentAnonSubmission(Long anonSessionId) {
        return anonSessionStudentIdSubmissionRepository.findByAnonSessionIdAndReplacedAtIsNull(anonSessionId);
    }

    @Transactional
    public void discardAnonSubmission(Long anonSessionId) {
        anonSessionStudentIdSubmissionRepository.findByAnonSessionIdAndReplacedAtIsNull(anonSessionId)
                .ifPresent(AnonSessionStudentIdSubmission::replace);
    }

    @Transactional
    public void transferToUser(Long anonSessionId, Long userId) {
        List<AnonSessionStudentIdSubmission> anonSubmissions = anonSessionStudentIdSubmissionRepository
                .findAllByAnonSessionIdOrderByIdAsc(anonSessionId);

        anonSubmissions.forEach(anonSubmission ->
                studentIdSubmissionRepository.save(StudentIdSubmission.transfer(anonSubmission, userId)));

        anonSessionStudentIdSubmissionRepository.deleteAll(anonSubmissions);

        anonSubmissions.stream()
                .filter(AnonSessionStudentIdSubmission::isCurrent)
                .findFirst()
                .ifPresent(current -> eventPublisher.publishEvent(new StudentIdReviewRequestedEvent(
                        userId, current.getSubmissionId(), current.getOrganization(), false)));
    }

    public PhotoPresignResult presignForUser(Long userId, String contentType) {
        return studentIdPhotoStorage.presign(StudentIdPhotoOwner.user(userId), contentType);
    }

    @Transactional
    public void resubmit(Long userId, String key) {
        User user = userRepository.findWithLockById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        boolean retried = studentIdSubmissionRepository.findFirstByUserIdOrderByIdDesc(userId)
                .map(latest -> latest.getUploadKey().equals(key))
                .orElse(false);

        if (retried) {
            return;
        }

        if (user.getSchoolVerificationStatus() != SchoolVerificationStatus.REJECTED) {
            throw new BusinessException(ErrorCode.SCHOOL_VERIFICATION_NOT_REJECTED);
        }

        requireUnusedKey(studentIdSubmissionRepository.existsByUploadKey(key), key);
        studentIdPhotoStorage.requireUploaded(StudentIdPhotoOwner.user(userId), key);

        UUID submissionId = UUID.randomUUID();
        String reviewKey = studentIdPhotoStorage.copyToReview(key, submissionId);

        studentIdSubmissionRepository.save(StudentIdSubmission.create(
                submissionId, userId, user.getOrganization(), key, reviewKey));

        user.startStudentIdVerification();

        eventPublisher.publishEvent(new StudentIdReviewRequestedEvent(userId, submissionId, user.getOrganization(), true));
    }

    private void requireUnusedKey(boolean used, String key) {
        if (used) {
            throw new BusinessException(ErrorCode.STUDENT_ID_PHOTO_NOT_FOUND, "이미 제출에 쓴 학생증 key입니다: " + key);
        }
    }
}
