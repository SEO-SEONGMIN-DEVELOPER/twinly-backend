package com.nidus.twinly.relationship.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum RelationshipType {
    @JsonProperty("acquaintance") ACQUAINTANCE(0),
    @JsonProperty("friend")       FRIEND(35),
    @JsonProperty("bestFriend")   BEST_FRIEND(70);

    private final int minIntimacy;

    RelationshipType(int minIntimacy) {
        this.minIntimacy = minIntimacy;
    }

    public int minIntimacy() {
        return minIntimacy;
    }

    public static RelationshipType fromIntimacy(int intimacy) {
        if (intimacy >= BEST_FRIEND.minIntimacy) {
            return BEST_FRIEND;
        }
        if (intimacy >= FRIEND.minIntimacy) {
            return FRIEND;
        }
        return ACQUAINTANCE;
    }
}
