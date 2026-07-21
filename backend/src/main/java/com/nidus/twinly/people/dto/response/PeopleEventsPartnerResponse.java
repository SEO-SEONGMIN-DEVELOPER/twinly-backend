package com.nidus.twinly.people.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.people.dto.result.PeopleEventsPartnerResult;
import com.nidus.twinly.relationship.domain.RelationshipSpecificType;
import com.nidus.twinly.user.domain.AvatarPaletteColor;

public record PeopleEventsPartnerResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        String userName,
        String profilePhotoUrl,
        AvatarPaletteColor avatarPaletteColor,
        Integer intimacy,
        RelationshipSpecificType relationshipSpecificType
) {

    public static PeopleEventsPartnerResponse from(PeopleEventsPartnerResult result) {
        return new PeopleEventsPartnerResponse(
                result.userId(),
                result.userName(),
                result.profilePhotoUrl(),
                result.avatarPaletteColor(),
                result.intimacy(),
                result.relationshipSpecificType()
        );
    }
}
