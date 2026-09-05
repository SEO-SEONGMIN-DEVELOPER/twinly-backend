package com.nidus.twinly.parallelrelation.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.common.parallel.ParallelRelationType;
import com.nidus.twinly.common.parallel.ParallelScoreBand;
import com.nidus.twinly.parallelrelation.dto.result.ParallelRelationDetailResult;

import java.time.Instant;
import java.util.List;

public record ParallelRelationDetailResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long parallelRelationId,
        ParallelRelationUserResponse user,
        ParallelRelationUserResponse partner,
        Integer similarity,
        Double topPercent,
        ParallelRelationType relation,
        String title,
        String story,
        List<ParallelScoreBand> scoreDistribution,
        Instant createdAt
) {

    public static ParallelRelationDetailResponse from(ParallelRelationDetailResult result) {
        return new ParallelRelationDetailResponse(
                result.parallelRelationId(),
                ParallelRelationUserResponse.from(result.user()),
                ParallelRelationUserResponse.from(result.partner()),
                result.similarity(),
                result.topPercent(),
                result.relation(),
                result.title(),
                result.story(),
                result.scoreDistribution(),
                result.createdAt()
        );
    }
}
