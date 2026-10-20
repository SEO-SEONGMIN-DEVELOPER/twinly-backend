package com.nidus.twinly.admin.domain;

public enum CaseStatusFilter {
    PENDING,
    IN_REVIEW,
    DONE;

    public String reportCondition(String column) {
        return switch (this) {
            case PENDING -> column + " = 'PENDING'";
            case IN_REVIEW -> column + " = 'IN_REVIEW'";
            case DONE -> column + " IN ('RESOLVED', 'REJECTED')";
        };
    }
}
