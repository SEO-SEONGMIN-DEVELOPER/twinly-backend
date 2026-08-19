package com.nidus.twinly.common.parallel;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ParallelRelationType {
    @JsonProperty("enemy") ENEMY,
    @JsonProperty("stranger") STRANGER,
    @JsonProperty("awkward") AWKWARD,
    @JsonProperty("close") CLOSE,
    @JsonProperty("bestFriend") BEST_FRIEND
}
