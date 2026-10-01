package com.nidus.twinly.me.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import com.nidus.twinly.me.dto.result.MeProfileV2Result;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record MeProfileV2Response(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        String userName,
        @Schema(nullable = true)
        ProfilePhotoInfo profilePhoto,
        List<String> interests,
        Integer encounteredPeopleCount,
        Integer encounteredFriendCount
) {

    public static MeProfileV2Response from(MeProfileV2Result result) {
        return new MeProfileV2Response(
                result.userId(),
                result.userName(),
                result.profilePhoto(),
                result.interests(),
                result.encounteredPeopleCount(),
                result.encounteredFriendCount()
        );
    }
}
