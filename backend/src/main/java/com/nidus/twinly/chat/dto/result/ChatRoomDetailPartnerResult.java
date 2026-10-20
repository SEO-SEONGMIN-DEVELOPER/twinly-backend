package com.nidus.twinly.chat.dto.result;

import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import com.nidus.twinly.relationship.domain.RelationshipSpecificType;

import java.util.List;

public record ChatRoomDetailPartnerResult(
        Long userId,
        String userName,
        Gender gender,
        String organization,
        String birthYear,
        ProfilePhotoInfo profilePhoto,
        Integer intimacy,
        Integer gameIntimacy,
        RelationshipSpecificType relationshipSpecificType,
        ChatRoomDetailDisclosedFieldsResult disclosedFields,
        List<String> interests
) {
}
