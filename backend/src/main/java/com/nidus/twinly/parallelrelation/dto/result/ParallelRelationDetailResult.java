package com.nidus.twinly.parallelrelation.dto.result;

import com.nidus.twinly.common.parallel.ParallelRelationType;
import com.nidus.twinly.common.parallel.ParallelScoreBand;

import java.time.Instant;
import java.util.List;

public record ParallelRelationDetailResult(
        Long parallelRelationId,
        ParallelRelationUserResult user,
        ParallelRelationUserResult partner,
        Integer similarity,
        Double topPercent,
        ParallelRelationType relation,
        String title,
        String story,
        List<ParallelScoreBand> scoreDistribution,
        Instant createdAt
) {
}
