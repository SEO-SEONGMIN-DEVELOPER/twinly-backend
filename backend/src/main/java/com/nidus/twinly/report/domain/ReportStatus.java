package com.nidus.twinly.report.domain;

public enum ReportStatus {
    PENDING,
    IN_REVIEW,
    REJECTED,
    RESOLVED;

    public boolean isProcessed() {
        return this == REJECTED || this == RESOLVED;
    }
}