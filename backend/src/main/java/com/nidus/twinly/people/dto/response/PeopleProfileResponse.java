package com.nidus.twinly.people.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import com.nidus.twinly.people.dto.result.PeopleProfileResult;
import com.nidus.twinly.relationship.domain.RelationshipSpecificType;
import com.nidus.twinly.relationship.domain.RelationshipType;
import io.swagger.v3.oas.annotations.media.Schema;

public record PeopleProfileResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        String userName,
        @Schema(nullable = true)
        ProfilePhotoInfo profilePhoto,
        Integer intimacy,
        RelationshipType relationshipType,
        RelationshipSpecificType relationshipSpecificType,
        Boolean isFavorited,
        PeopleProfileDisclosedFieldsResponse disclosedFields,
        Boolean isDeleted,
        Boolean isBlocked
) {

    public static PeopleProfileResponse from(PeopleProfileResult result) {
        return new PeopleProfileResponse(
                result.userId(),
                result.userName(),
                result.profilePhoto(),
                result.intimacy(),
                result.relationshipType(),
                result.relationshipSpecificType(),
                result.isFavorited(),
                result.disclosedFields() != null ? PeopleProfileDisclosedFieldsResponse.from(result.disclosedFields()) : null,
                result.isDeleted(),
                result.isBlocked()
        );
    }
}
