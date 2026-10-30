package com.nidus.twinly.season.dto.response;

import com.nidus.twinly.purchase.domain.PoolAssignment;
import com.nidus.twinly.season.dto.result.SeasonPoolReassignResult;

import java.util.List;

public record SeasonPoolReassignResponse(
        int assignedCount,
        List<PoolAssignment> pools
) {

    public static SeasonPoolReassignResponse from(SeasonPoolReassignResult result) {
        return new SeasonPoolReassignResponse(result.assignedCount(), result.pools());
    }
}
