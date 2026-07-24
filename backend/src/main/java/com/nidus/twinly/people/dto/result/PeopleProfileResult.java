package com.nidus.twinly.people.dto.result;

import com.nidus.twinly.relationship.domain.RelationshipSpecificType;
import com.nidus.twinly.relationship.domain.RelationshipType;

public record PeopleProfileResult(
        Long userId,
        String userName,
        String profilePhotoUrl,
        Integer intimacy,
        RelationshipType relationshipType,
        RelationshipSpecificType relationshipSpecificType,
        Boolean isFavorited,
        Boolean isHighlighted,
        PeopleProfileDisclosedFieldsResult disclosedFields,
        Boolean isDeleted,
        Boolean isBlocked
) {
}
