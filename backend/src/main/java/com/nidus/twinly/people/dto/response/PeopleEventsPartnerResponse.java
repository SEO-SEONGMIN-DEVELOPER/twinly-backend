package com.nidus.twinly.people.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import com.nidus.twinly.people.dto.result.PeopleEventsPartnerResult;
import com.nidus.twinly.relationship.domain.RelationshipSpecificType;
import io.swagger.v3.oas.annotations.media.Schema;

public record PeopleEventsPartnerResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        String userName,
        @Schema(nullable = true)
        ProfilePhotoInfo profilePhoto,
        Integer intimacy,
        RelationshipSpecificType relationshipSpecificType
) {

    public static PeopleEventsPartnerResponse from(PeopleEventsPartnerResult result) {
        return new PeopleEventsPartnerResponse(
                result.userId(),
                result.userName(),
                result.profilePhoto(),
                result.intimacy(),
                result.relationshipSpecificType()
        );
    }
}
