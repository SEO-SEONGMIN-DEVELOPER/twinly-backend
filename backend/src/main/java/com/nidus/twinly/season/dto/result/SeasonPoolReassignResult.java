package com.nidus.twinly.season.dto.result;

import com.nidus.twinly.purchase.domain.PoolAssignment;

import java.util.List;

public record SeasonPoolReassignResult(
        int assignedCount,
        List<PoolAssignment> pools
) {

    public static SeasonPoolReassignResult from(List<PoolAssignment> pools) {
        return new SeasonPoolReassignResult(pools.stream().mapToInt(PoolAssignment::memberCount).sum(), pools);
    }
}
