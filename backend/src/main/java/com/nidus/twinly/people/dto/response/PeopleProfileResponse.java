package com.nidus.twinly.people.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.people.dto.result.PeopleProfileResult;
import com.nidus.twinly.relationship.domain.RelationshipSpecificType;
import com.nidus.twinly.relationship.domain.RelationshipType;
import io.swagger.v3.oas.annotations.media.Schema;

public record PeopleProfileResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        String userName,
        @Schema(nullable = true)
        String profilePhotoUrl,
        Integer intimacy,
        RelationshipType relationshipType,
        RelationshipSpecificType relationshipSpecificType,
        Boolean isFavorited,
        Boolean isHighlighted,
        PeopleProfileDisclosedFieldsResponse disclosedFields,
        Boolean isDeleted,
        Boolean isBlocked
) {

    public static PeopleProfileResponse from(PeopleProfileResult result) {
        return new PeopleProfileResponse(
                result.userId(),
                result.userName(),
                result.profilePhotoUrl(),
                result.intimacy(),
                result.relationshipType(),
                result.relationshipSpecificType(),
                result.isFavorited(),
                result.isHighlighted(),
                result.disclosedFields() != null ? PeopleProfileDisclosedFieldsResponse.from(result.disclosedFields()) : null,
                result.isDeleted(),
                result.isBlocked()
        );
    }
}
