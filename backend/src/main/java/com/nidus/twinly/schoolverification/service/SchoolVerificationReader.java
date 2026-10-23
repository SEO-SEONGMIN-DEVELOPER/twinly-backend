package com.nidus.twinly.schoolverification.service;

import com.nidus.twinly.schoolverification.domain.SchoolVerificationStatus;
import com.nidus.twinly.schoolverification.domain.StudentIdRejectReason;
import com.nidus.twinly.schoolverification.entity.StudentIdSubmission;
import com.nidus.twinly.schoolverification.repository.StudentIdSubmissionRepository;
import com.nidus.twinly.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SchoolVerificationReader {

    private final StudentIdSubmissionRepository studentIdSubmissionRepository;

    public StudentIdRejectReason rejectReason(User user) {
        if (user.getSchoolVerificationStatus() != SchoolVerificationStatus.REJECTED) {
            return null;
        }

        return studentIdSubmissionRepository.findFirstByUserIdOrderByIdDesc(user.getId())
                .map(StudentIdSubmission::getRejectReason)
                .orElse(null);
    }
}
