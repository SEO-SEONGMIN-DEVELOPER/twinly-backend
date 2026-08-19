package com.nidus.twinly.showcase.dto.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.nidus.twinly.showcase.dto.result.ShowcaseBubbleLineResult;
import com.nidus.twinly.showcase.dto.result.ShowcaseLineResult;
import com.nidus.twinly.showcase.dto.result.ShowcaseNarrationLineResult;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "t")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ShowcaseNarrationLineResponse.class, name = "narr"),
        @JsonSubTypes.Type(value = ShowcaseBubbleLineResponse.class, name = "bubble")
})
public sealed interface ShowcaseLineResponse permits ShowcaseNarrationLineResponse, ShowcaseBubbleLineResponse {

    static ShowcaseLineResponse from(ShowcaseLineResult result) {
        return switch (result) {
            case ShowcaseNarrationLineResult line -> ShowcaseNarrationLineResponse.from(line);
            case ShowcaseBubbleLineResult line -> ShowcaseBubbleLineResponse.from(line);
        };
    }
}
