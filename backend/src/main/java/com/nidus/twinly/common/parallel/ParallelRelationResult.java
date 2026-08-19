package com.nidus.twinly.common.parallel;

public record ParallelRelationResult(
        ParallelRelationType relation,
        String title,
        String story
) {
}
