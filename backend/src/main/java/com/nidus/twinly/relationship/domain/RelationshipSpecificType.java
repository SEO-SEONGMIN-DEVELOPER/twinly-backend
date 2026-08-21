package com.nidus.twinly.relationship.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum RelationshipSpecificType {
    @JsonProperty("스친 인연")         PASSING,
    @JsonProperty("어색한 사이")       AWKWARD,
    @JsonProperty("알아가는 중")       GETTING_TO_KNOW,
    @JsonProperty("조금 편해진 사이")   WARMING_UP,
    @JsonProperty("친한 사이")         CLOSE,
    @JsonProperty("자주 찾는 사이")     GO_TO,
    @JsonProperty("특별한 사이")       SPECIAL,
    @JsonProperty("평생 갈 인연")      LIFELONG;

    public static RelationshipSpecificType fromIntimacy(int intimacy) {
        if (intimacy < 6) {
            return PASSING;
        } else if (intimacy < 10) {
            return AWKWARD;
        } else if (intimacy < 20) {
            return GETTING_TO_KNOW;
        } else if (intimacy < 35) {
            return WARMING_UP;
        } else if (intimacy < 50) {
            return CLOSE;
        } else if (intimacy < 70) {
            return GO_TO;
        } else if (intimacy < 85) {
            return SPECIAL;
        } else {
            return LIFELONG;
        }
    }
}
