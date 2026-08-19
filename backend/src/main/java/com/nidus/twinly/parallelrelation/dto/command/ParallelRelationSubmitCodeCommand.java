package com.nidus.twinly.parallelrelation.dto.command;

import com.nidus.twinly.parallelrelation.dto.request.ParallelRelationSubmitCodeRequest;

public record ParallelRelationSubmitCodeCommand(
        String code
) {

    public static ParallelRelationSubmitCodeCommand from(ParallelRelationSubmitCodeRequest request) {
        return new ParallelRelationSubmitCodeCommand(request.code());
    }
}
