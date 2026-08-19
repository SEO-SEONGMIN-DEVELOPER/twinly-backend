package com.nidus.twinly.parallelrelation.dto.result;

import java.util.List;

public record ParallelRelationListResult(
        List<ParallelRelationListItemResult> relations
) {
}
