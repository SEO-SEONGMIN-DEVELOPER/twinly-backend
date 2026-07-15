package com.nidus.twinly.chat.dto.response;

import com.nidus.twinly.chat.dto.result.ChatRoomDetailPartnerResult;
import com.nidus.twinly.relationship.domain.RelationshipSpecificType;

public record ChatRoomDetailPartnerResponse(
        Long userId,
        String userName,
        String profilePhotoUrl,
        Integer rapport,
        RelationshipSpecificType relationshipSpecificType,
        ChatRoomDetailDisclosedFieldsResponse disclosedFields
) {

    public static ChatRoomDetailPartnerResponse from(ChatRoomDetailPartnerResult result) {
        return new ChatRoomDetailPartnerResponse(
                result.userId(),
                result.userName(),
                result.profilePhotoUrl(),
                result.rapport(),
                result.relationshipSpecificType(),
                ChatRoomDetailDisclosedFieldsResponse.from(result.disclosedFields())
        );
    }
}
