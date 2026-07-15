package com.nidus.twinly.relationship.domain;

public enum RelationshipSpecificType {
    RELATIONSHIP_SPECIFIC_TYPE_1,
    RELATIONSHIP_SPECIFIC_TYPE_2,
    RELATIONSHIP_SPECIFIC_TYPE_3;

    public static RelationshipSpecificType fromRapport(int rapport) {
        if (rapport < 30) {
            return RELATIONSHIP_SPECIFIC_TYPE_1;
        } else if (rapport < 70) {
            return RELATIONSHIP_SPECIFIC_TYPE_2;
        } else {
            return RELATIONSHIP_SPECIFIC_TYPE_3;
        }
    }
}
