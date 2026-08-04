package com.nidus.twinly.relationship.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum RelationshipType {
    @JsonProperty("acquaintance") ACQUAINTANCE,
    @JsonProperty("friend")       FRIEND,
    @JsonProperty("bestFriend")   BEST_FRIEND;

    public static RelationshipType fromIntimacy(int intimacy) {
        if (intimacy < 30) {
            return ACQUAINTANCE;
        } else if (intimacy < 70) {
            return FRIEND;
        } else {
            return BEST_FRIEND;
        }
    }
}
