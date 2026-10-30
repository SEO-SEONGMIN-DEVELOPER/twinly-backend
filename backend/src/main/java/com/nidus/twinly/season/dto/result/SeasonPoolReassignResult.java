package com.nidus.twinly.season.dto.result;

import com.nidus.twinly.purchase.domain.PoolAssignment;

import java.util.List;

public record SeasonPoolReassignResult(
        int assignedCount,
        boolean alreadyReassigned,
        List<PoolAssignment> pools
) {

    public static SeasonPoolReassignResult of(List<PoolAssignment> pools, boolean alreadyReassigned) {
        return new SeasonPoolReassignResult(pools.stream().mapToInt(PoolAssignment::memberCount).sum(), alreadyReassigned, pools);
    }
}
