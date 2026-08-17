package com.nidus.twinly.me.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import com.nidus.twinly.me.dto.result.MeProfileResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record MeProfileResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        String userName,
        @Schema(nullable = true)
        ProfilePhotoInfo profilePhoto,
        String persona,
        List<String> interests,
        Integer encounteredPeopleCount,
        Integer encounteredFriendCount
) {

    public static MeProfileResponse from(MeProfileResult result) {
        return new MeProfileResponse(
                result.userId(),
                result.userName(),
                result.profilePhoto(),
                result.persona(),
                result.interests(),
                result.encounteredPeopleCount(),
                result.encounteredFriendCount()
        );
    }
}
