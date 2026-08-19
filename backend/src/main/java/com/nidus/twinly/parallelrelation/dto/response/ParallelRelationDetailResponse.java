package com.nidus.twinly.parallelrelation.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.common.parallel.ParallelRelationType;
import com.nidus.twinly.parallelrelation.dto.result.ParallelRelationDetailResult;

import java.time.Instant;

public record ParallelRelationDetailResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long parallelRelationId,
        ParallelRelationUserResponse user,
        ParallelRelationUserResponse partner,
        Integer similarity,
        ParallelRelationType relation,
        String title,
        String story,
        Instant createdAt
) {

    public static ParallelRelationDetailResponse from(ParallelRelationDetailResult result) {
        return new ParallelRelationDetailResponse(
                result.parallelRelationId(),
                ParallelRelationUserResponse.from(result.user()),
                ParallelRelationUserResponse.from(result.partner()),
                result.similarity(),
                result.relation(),
                result.title(),
                result.story(),
                result.createdAt()
        );
    }
}
