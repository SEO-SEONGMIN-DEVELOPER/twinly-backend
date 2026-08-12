package com.nidus.twinly.user.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.user.dto.result.UsersPageResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record UsersPageResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(nullable = true)
        Long nextCursor,
        Boolean hasMore
) {

    public static UsersPageResponse from(UsersPageResult result) {
        return new UsersPageResponse(result.nextCursor(), result.hasMore());
    }
}
