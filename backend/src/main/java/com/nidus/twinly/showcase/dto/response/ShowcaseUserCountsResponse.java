package com.nidus.twinly.showcase.dto.response;

import com.nidus.twinly.showcase.dto.result.ShowcaseUserCountsResult;

public record ShowcaseUserCountsResponse(
        Integer total,
        Integer sameOrganization
) {

    public static ShowcaseUserCountsResponse from(ShowcaseUserCountsResult result) {
        return new ShowcaseUserCountsResponse(result.total(), result.sameOrganization());
    }
}
