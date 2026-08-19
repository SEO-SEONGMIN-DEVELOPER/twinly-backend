package com.nidus.twinly.parallelrelation.dto.response;

import com.nidus.twinly.parallelrelation.dto.result.ParallelRelationIssueCodeResult;

public record ParallelRelationIssueCodeResponse(
        String code,
        String shareMessage
) {

    public static ParallelRelationIssueCodeResponse from(ParallelRelationIssueCodeResult result) {
        return new ParallelRelationIssueCodeResponse(result.code(), result.shareMessage());
    }
}
