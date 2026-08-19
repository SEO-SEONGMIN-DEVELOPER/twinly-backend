package com.nidus.twinly.parallelrelation.dto.result;

public record ParallelRelationSubmitCodeResult(
        boolean created,
        ParallelRelationDetailResult relation
) {
}
