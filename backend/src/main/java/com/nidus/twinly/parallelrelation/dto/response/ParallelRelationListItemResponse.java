package com.nidus.twinly.parallelrelation.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.common.parallel.ParallelRelationType;
import com.nidus.twinly.parallelrelation.dto.result.ParallelRelationListItemResult;

import java.time.Instant;

public record ParallelRelationListItemResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long parallelRelationId,
        ParallelRelationUserResponse partner,
        ParallelRelationType relation,
        String title,
        Integer similarity,
        Double topPercent,
        Instant createdAt
) {

    public static ParallelRelationListItemResponse from(ParallelRelationListItemResult result) {
        return new ParallelRelationListItemResponse(
                result.parallelRelationId(),
                ParallelRelationUserResponse.from(result.partner()),
                result.relation(),
                result.title(),
                result.similarity(),
                result.topPercent(),
                result.createdAt()
        );
    }
}
