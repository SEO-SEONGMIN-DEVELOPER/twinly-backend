package com.nidus.twinly.common.parallel;

import java.util.List;

public record ParallelRelationStories(
        ParallelRelationType relation,
        List<ParallelRelationContent> stories
) {
}
