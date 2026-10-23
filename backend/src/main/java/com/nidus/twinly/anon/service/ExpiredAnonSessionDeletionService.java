package com.nidus.twinly.anon.service;

import com.nidus.twinly.aichat.repository.AnonSessionAiChatRepository;
import com.nidus.twinly.anon.entity.AnonSessionPhoto;
import com.nidus.twinly.anon.repository.AnonSessionAgreementRepository;
import com.nidus.twinly.anon.repository.AnonSessionPersonaElementRepository;
import com.nidus.twinly.anon.repository.AnonSessionPhotoRepository;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.auth.repository.AnonSessionIdentityVerificationRepository;
import com.nidus.twinly.auth.repository.AnonSessionVerificationSessionRepository;
import com.nidus.twinly.common.aws.s3.S3Service;
import com.nidus.twinly.onboarding.repository.SurveyAnswerRepository;
import com.nidus.twinly.schoolverification.entity.AnonSessionStudentIdSubmission;
import com.nidus.twinly.schoolverification.repository.AnonSessionStudentIdSubmissionRepository;
import com.nidus.twinly.schoolverification.storage.StudentIdPhotoStorage;
import com.nidus.twinly.user.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpiredAnonSessionDeletionService {

    private final S3Service s3Service;
    private final StudentIdPhotoStorage studentIdPhotoStorage;

    private final AnonSessionRepository anonSessionRepository;
    private final AnonSessionVerificationSessionRepository anonSessionVerificationSessionRepository;
    private final AnonSessionIdentityVerificationRepository anonSessionIdentityVerificationRepository;
    private final AnonSessionPhotoRepository anonSessionPhotoRepository;
    private final AnonSessionAgreementRepository anonSessionAgreementRepository;
    private final AnonSessionPersonaElementRepository anonSessionPersonaElementRepository;
    private final AnonSessionAiChatRepository anonSessionAiChatRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;
    private final AnonSessionStudentIdSubmissionRepository anonSessionStudentIdSubmissionRepository;
    private final PhotoRepository photoRepository;

    public void deleteFiles(Long anonSessionId) {
        anonSessionStudentIdSubmissionRepository.findAllByAnonSessionIdOrderByIdAsc(anonSessionId).stream()
                .filter(submission -> submission.getPhotoDeletedAt() == null)
                .forEach(submission -> studentIdPhotoStorage.delete(submission.getUploadKey(), submission.getReviewKey()));

        anonSessionPhotoRepository.findAllByAnonSessionId(anonSessionId).stream()
                .map(AnonSessionPhoto::getKey)
                .filter(key -> photoRepository.countReferencesByKey(key) == 0)
                .forEach(s3Service::delete);
    }

    @Transactional
    public void deleteRows(Long anonSessionId) {
        anonSessionVerificationSessionRepository.deleteByAnonSessionId(anonSessionId);
        anonSessionIdentityVerificationRepository.deleteByAnonSessionId(anonSessionId);
        anonSessionPhotoRepository.deleteAll(anonSessionPhotoRepository.findAllByAnonSessionId(anonSessionId));
        anonSessionAgreementRepository.deleteAll(anonSessionAgreementRepository.findAllByAnonSessionId(anonSessionId));
        anonSessionPersonaElementRepository.deleteAll(anonSessionPersonaElementRepository.findAllByAnonSessionId(anonSessionId));
        anonSessionAiChatRepository.deleteAll(anonSessionAiChatRepository.findAllByAnonSessionId(anonSessionId));
        surveyAnswerRepository.deleteByAnonSessionId(anonSessionId);

        List<AnonSessionStudentIdSubmission> studentIdSubmissions = anonSessionStudentIdSubmissionRepository.findAllByAnonSessionIdOrderByIdAsc(anonSessionId);
        anonSessionStudentIdSubmissionRepository.deleteAll(studentIdSubmissions);

        anonSessionRepository.deleteById(anonSessionId);
    }
}
