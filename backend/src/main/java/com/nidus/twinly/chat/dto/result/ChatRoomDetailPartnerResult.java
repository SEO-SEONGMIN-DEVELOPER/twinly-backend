package com.nidus.twinly.chat.dto.result;

import com.nidus.twinly.relationship.domain.RelationshipSpecificType;

public record ChatRoomDetailPartnerResult(
        Long userId,
        String userName,
        String profilePhotoUrl,
        Integer intimacy,
        RelationshipSpecificType relationshipSpecificType,
        ChatRoomDetailDisclosedFieldsResult disclosedFields
) {
}
