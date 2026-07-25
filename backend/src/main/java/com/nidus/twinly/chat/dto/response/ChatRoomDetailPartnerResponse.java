package com.nidus.twinly.chat.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.chat.dto.result.ChatRoomDetailPartnerResult;
import io.swagger.v3.oas.annotations.media.Schema;
import com.nidus.twinly.relationship.domain.RelationshipSpecificType;

public record ChatRoomDetailPartnerResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        String userName,
        @Schema(nullable = true)
        String profilePhotoUrl,
        Integer intimacy,
        RelationshipSpecificType relationshipSpecificType,
        ChatRoomDetailDisclosedFieldsResponse disclosedFields
) {

    public static ChatRoomDetailPartnerResponse from(ChatRoomDetailPartnerResult result) {
        return new ChatRoomDetailPartnerResponse(
                result.userId(),
                result.userName(),
                result.profilePhotoUrl(),
                result.intimacy(),
                result.relationshipSpecificType(),
                ChatRoomDetailDisclosedFieldsResponse.from(result.disclosedFields())
        );
    }
}
