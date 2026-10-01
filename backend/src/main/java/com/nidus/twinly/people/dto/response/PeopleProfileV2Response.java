package com.nidus.twinly.people.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import com.nidus.twinly.people.dto.result.PeopleProfileV2Result;
import com.nidus.twinly.relationship.domain.RelationshipSpecificType;
import com.nidus.twinly.relationship.domain.RelationshipType;
import io.swagger.v3.oas.annotations.media.Schema;

public record PeopleProfileV2Response(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        String userName,
        @Schema(nullable = true)
        Gender gender,
        @Schema(nullable = true)
        String organization,
        @Schema(nullable = true)
        String birthYear,
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

    public static PeopleProfileV2Response from(PeopleProfileV2Result result) {
        return new PeopleProfileV2Response(
                result.userId(),
                result.userName(),
                result.gender(),
                result.organization(),
                result.birthYear(),
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
