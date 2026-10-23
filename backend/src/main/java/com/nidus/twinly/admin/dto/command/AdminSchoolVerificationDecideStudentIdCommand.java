package com.nidus.twinly.admin.dto.command;

import com.nidus.twinly.admin.dto.request.AdminSchoolVerificationDecideStudentIdRequest;
import com.nidus.twinly.schoolverification.domain.StudentIdDecision;
import com.nidus.twinly.schoolverification.domain.StudentIdRejectReason;

public record AdminSchoolVerificationDecideStudentIdCommand(
        StudentIdDecision decision,
        StudentIdRejectReason rejectReason,
        String reviewer
) {

    public static AdminSchoolVerificationDecideStudentIdCommand from(AdminSchoolVerificationDecideStudentIdRequest request) {
        return new AdminSchoolVerificationDecideStudentIdCommand(
                request.decision(),
                request.rejectReason(),
                request.reviewer().trim()
        );
    }
}
