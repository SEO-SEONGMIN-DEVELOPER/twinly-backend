package com.nidus.twinly.parallelrelation.dto.response;

import com.nidus.twinly.parallelrelation.dto.result.ParallelRelationListResult;

import java.util.List;

public record ParallelRelationListResponse(
        List<ParallelRelationListItemResponse> relations
) {

    public static ParallelRelationListResponse from(ParallelRelationListResult result) {
        return new ParallelRelationListResponse(
                result.relations().stream().map(ParallelRelationListItemResponse::from).toList()
        );
    }
}
