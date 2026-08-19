package com.nidus.twinly.parallelrelation.dto.result;

import com.nidus.twinly.common.parallel.ParallelRelationType;

import java.time.Instant;

public record ParallelRelationListItemResult(
        Long parallelRelationId,
        ParallelRelationUserResult partner,
        ParallelRelationType relation,
        String title,
        Integer similarity,
        Instant createdAt
) {
}
