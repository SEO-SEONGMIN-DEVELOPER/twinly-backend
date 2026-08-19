package com.nidus.twinly.parallelrelation.dto.result;

import com.nidus.twinly.common.parallel.ParallelRelationType;

import java.time.Instant;

public record ParallelRelationDetailResult(
        Long parallelRelationId,
        ParallelRelationUserResult user,
        ParallelRelationUserResult partner,
        Integer similarity,
        ParallelRelationType relation,
        String title,
        String story,
        Instant createdAt
) {
}
