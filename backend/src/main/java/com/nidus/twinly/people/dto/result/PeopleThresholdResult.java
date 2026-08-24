package com.nidus.twinly.people.dto.result;

import com.nidus.twinly.relationship.domain.RelationshipType;

public record PeopleThresholdResult(
        Integer acquaintance,
        Integer friend,
        Integer bestFriend
) {

    public static PeopleThresholdResult of() {
        return new PeopleThresholdResult(
                RelationshipType.ACQUAINTANCE.minIntimacy(),
                RelationshipType.FRIEND.minIntimacy(),
                RelationshipType.BEST_FRIEND.minIntimacy()
        );
    }
}
