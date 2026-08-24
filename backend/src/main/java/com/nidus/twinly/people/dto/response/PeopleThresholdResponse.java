package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.people.dto.result.PeopleThresholdResult;

public record PeopleThresholdResponse(
        Integer acquaintance,
        Integer friend,
        Integer bestFriend
) {

    public static PeopleThresholdResponse from(PeopleThresholdResult result) {
        return new PeopleThresholdResponse(result.acquaintance(), result.friend(), result.bestFriend());
    }
}
