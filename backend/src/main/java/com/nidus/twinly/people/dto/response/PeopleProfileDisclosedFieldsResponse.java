package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.people.dto.result.PeopleProfileDisclosedFieldsResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record PeopleProfileDisclosedFieldsResponse(
        @Schema(nullable = true)
        String affiliation,
        @Schema(nullable = true)
        String affiliationNumber
) {

    public static PeopleProfileDisclosedFieldsResponse from(PeopleProfileDisclosedFieldsResult result) {
        return new PeopleProfileDisclosedFieldsResponse(result.affiliation(), result.affiliationNumber());
    }
}
