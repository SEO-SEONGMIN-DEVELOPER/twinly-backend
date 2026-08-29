package com.nidus.twinly.chat.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.chat.dto.result.ChatRoomDetailPartnerResult;
import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import com.nidus.twinly.relationship.domain.RelationshipSpecificType;

import java.util.List;

public record ChatRoomDetailPartnerResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        String userName,
        @Schema(nullable = true)
        ProfilePhotoInfo profilePhoto,
        Integer intimacy,
        RelationshipSpecificType relationshipSpecificType,
        ChatRoomDetailDisclosedFieldsResponse disclosedFields,
        List<String> interests
) {

    public static ChatRoomDetailPartnerResponse from(ChatRoomDetailPartnerResult result) {
        return new ChatRoomDetailPartnerResponse(
                result.userId(),
                result.userName(),
                result.profilePhoto(),
                result.intimacy(),
                result.relationshipSpecificType(),
                ChatRoomDetailDisclosedFieldsResponse.from(result.disclosedFields()),
                result.interests()
        );
    }
}
