package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.people.dto.result.PeopleProfileDisclosedFieldsResult;

public record PeopleProfileDisclosedFieldsResponse(
        String affiliation,
        String birthDate
) {

    public static PeopleProfileDisclosedFieldsResponse from(PeopleProfileDisclosedFieldsResult result) {
        return new PeopleProfileDisclosedFieldsResponse(result.affiliation(), result.birthDate());
    }
}
