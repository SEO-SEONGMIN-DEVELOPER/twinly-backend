package com.nidus.twinly.chat.dto.result;

import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import com.nidus.twinly.relationship.domain.RelationshipSpecificType;

import java.util.List;

public record ChatRoomDetailPartnerResult(
        Long userId,
        String userName,
        ProfilePhotoInfo profilePhoto,
        Integer intimacy,
        RelationshipSpecificType relationshipSpecificType,
        ChatRoomDetailDisclosedFieldsResult disclosedFields,
        List<String> interests
) {
}
