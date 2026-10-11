package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.people.dto.result.PeoplePreviewsResult;

import java.util.List;

public record PeoplePreviewsResponse(
        List<PeoplePreviewResponse> previews
) {

    public static PeoplePreviewsResponse from(PeoplePreviewsResult result) {
        return new PeoplePreviewsResponse(result.previews().stream().map(PeoplePreviewResponse::from).toList());
    }
}
