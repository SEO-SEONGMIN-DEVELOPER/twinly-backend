package com.nidus.twinly.user.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.user.dto.result.UsersResult;

import java.util.List;

public record UsersResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        List<Long> userIds,
        UsersPageResponse page
) {

    public static UsersResponse from(UsersResult result) {
        return new UsersResponse(result.userIds(), UsersPageResponse.from(result.page()));
    }
}
