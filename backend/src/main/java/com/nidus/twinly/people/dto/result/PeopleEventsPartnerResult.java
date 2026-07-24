package com.nidus.twinly.people.dto.result;

import com.nidus.twinly.relationship.domain.RelationshipSpecificType;

public record PeopleEventsPartnerResult(
        Long userId,
        String userName,
        String profilePhotoUrl,
        Integer intimacy,
        RelationshipSpecificType relationshipSpecificType
) {
}
