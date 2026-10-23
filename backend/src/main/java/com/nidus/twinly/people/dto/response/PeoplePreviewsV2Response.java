package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.people.dto.result.PeoplePreviewsV2Result;

import java.util.List;

public record PeoplePreviewsV2Response(
        List<PeoplePreviewV2Response> previews
) {

    public static PeoplePreviewsV2Response from(PeoplePreviewsV2Result result) {
        return new PeoplePreviewsV2Response(result.previews().stream().map(PeoplePreviewV2Response::from).toList());
    }
}
