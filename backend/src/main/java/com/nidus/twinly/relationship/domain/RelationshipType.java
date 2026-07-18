package com.nidus.twinly.relationship.domain;

public enum RelationshipType {
    RELATIONSHIP_TYPE_1,
    RELATIONSHIP_TYPE_2,
    RELATIONSHIP_TYPE_3;

    public static RelationshipType fromIntimacy(int intimacy) {
        if (intimacy < 30) {
            return RELATIONSHIP_TYPE_1;
        } else if (intimacy < 70) {
            return RELATIONSHIP_TYPE_2;
        } else {
            return RELATIONSHIP_TYPE_3;
        }
    }
}
