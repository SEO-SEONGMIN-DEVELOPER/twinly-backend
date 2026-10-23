package com.nidus.twinly.schoolverification.service;

import com.nidus.twinly.common.logging.ErrorLog;
import com.nidus.twinly.common.logging.InfoLog;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.schoolverification.entity.AnonSessionStudentIdSubmission;
import com.nidus.twinly.schoolverification.entity.StudentIdSubmission;
import com.nidus.twinly.schoolverification.repository.AnonSessionStudentIdSubmissionRepository;
import com.nidus.twinly.schoolverification.repository.StudentIdSubmissionRepository;
import com.nidus.twinly.schoolverification.storage.StudentIdPhotoStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentIdPhotoPurgeService {

    private static final int BATCH_SIZE = 500;

    private final StudentIdPhotoStorage studentIdPhotoStorage;
    private final StudentIdSubmissionRepository studentIdSubmissionRepository;
    private final AnonSessionStudentIdSubmissionRepository anonSessionStudentIdSubmissionRepository;

    public void purge() {
        int purged = 0;
        int failed = 0;

        for (StudentIdSubmission submission : studentIdSubmissionRepository.findPhotoPurgeTargets(PageRequest.of(0, BATCH_SIZE))) {
            if (deletePhoto(submission.getUploadKey(), submission.getReviewKey(), submission.getSubmissionId().toString())) {
                studentIdSubmissionRepository.markPhotoDeleted(submission.getId(), Instant.now());
                purged++;
            } else {
                failed++;
            }
        }

        for (AnonSessionStudentIdSubmission submission : anonSessionStudentIdSubmissionRepository.findPhotoPurgeTargets(PageRequest.of(0, BATCH_SIZE))) {
            if (deletePhoto(submission.getUploadKey(), submission.getReviewKey(), submission.getSubmissionId().toString())) {
                anonSessionStudentIdSubmissionRepository.markPhotoDeleted(submission.getId(), Instant.now());
                purged++;
            } else {
                failed++;
            }
        }

        if (purged > 0 || failed > 0) {
            InfoLog.log(log, "판정·교체된 학생증 사진을 지웠습니다.", field("purgedCount", purged), field("failedCount", failed));
        }
    }

    private boolean deletePhoto(String uploadKey, String reviewKey, String submissionId) {
        try {
            studentIdPhotoStorage.delete(uploadKey, reviewKey);
            return true;
        } catch (RuntimeException e) {
            ErrorLog.warn(log, ErrorCode.STUDENT_ID_PHOTO_DELETE_FAILED.name(), submissionId, e)
                    .log("학생증 사진 삭제 실패. 다음 주기에 다시 시도합니다.");
            return false;
        }
    }
}
