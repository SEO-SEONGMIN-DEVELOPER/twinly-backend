package com.nidus.twinly.people.controller;

import com.nidus.twinly.common.web.RequestId;
import com.nidus.twinly.people.dto.response.PeopleEventResponse;
import com.nidus.twinly.people.dto.response.PeopleEventsResponse;
import com.nidus.twinly.people.dto.response.PeopleIntimacySeriesResponse;
import com.nidus.twinly.people.dto.response.PeopleLearnedFactsResponse;
import com.nidus.twinly.people.dto.response.PeopleProfileResponse;
import com.nidus.twinly.people.dto.response.PeopleResponse;
import com.nidus.twinly.people.service.PeopleService;
import com.nidus.twinly.user.dto.header.UserInfo;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class PeopleController {

    private final PeopleService peopleService;

    @GetMapping("/api/v1/people")
    public PeopleResponse people(@AuthenticationPrincipal UserInfo userInfo,
                                    @RequestParam(required = false) String cursor,
                                    @RequestParam(required = false) @Min(1) @Max(100) Integer limit) {
        return PeopleResponse.from(peopleService.people(userInfo.id(), RequestId.toLongOrNull(cursor, "cursor"), limit));
    }

    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @GetMapping("/api/v1/people/{userId}/profile")
    public PeopleProfileResponse profile(@AuthenticationPrincipal UserInfo userInfo,
                                         @PathVariable("userId") String partnerUserId) {
        return PeopleProfileResponse.from(peopleService.profile(userInfo.id(), RequestId.toLong(partnerUserId, "userId")));
    }

    @ApiResponse(responseCode = "404", description = "ENCOUNTER_NOT_FOUND")
    @PutMapping("/api/v1/people/{userId}/favorite")
    public void favorite(@AuthenticationPrincipal UserInfo userInfo,
                          @PathVariable("userId") String partnerUserId) {
        peopleService.favorite(userInfo.id(), RequestId.toLong(partnerUserId, "userId"));
    }

    @ApiResponse(responseCode = "404", description = "ENCOUNTER_NOT_FOUND")
    @DeleteMapping("/api/v1/people/{userId}/favorite")
    public void deleteFavorite(@AuthenticationPrincipal UserInfo userInfo,
                                @PathVariable("userId") String partnerUserId) {
        peopleService.deleteFavorite(userInfo.id(), RequestId.toLong(partnerUserId, "userId"));
    }

    @ApiResponse(responseCode = "404", description = "RELATIONSHIP_NOT_FOUND")
    @GetMapping("/api/v1/people/{userId}/intimacy-series")
    public PeopleIntimacySeriesResponse intimacySeries(@AuthenticationPrincipal UserInfo userInfo,
                                                       @PathVariable("userId") String partnerUserId) {
        return PeopleIntimacySeriesResponse.from(peopleService.intimacySeries(userInfo.id(), RequestId.toLong(partnerUserId, "userId")));
    }

    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @GetMapping("/api/v1/people/{userId}/events")
    public PeopleEventsResponse events(@AuthenticationPrincipal UserInfo userInfo,
                                       @PathVariable("userId") String partnerUserId,
                                       @RequestParam(required = false) LocalDate cursor,
                                       @RequestParam(required = false) @Min(1) @Max(100) Integer limit) {
        return PeopleEventsResponse.from(peopleService.events(userInfo.id(), RequestId.toLong(partnerUserId, "userId"), cursor, limit));
    }

    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @GetMapping("/api/v1/people/{userId}/events/{date}")
    public PeopleEventResponse event(@AuthenticationPrincipal UserInfo userInfo,
                                     @PathVariable("userId") String partnerUserId,
                                     @PathVariable LocalDate date) {
        return PeopleEventResponse.from(peopleService.event(userInfo.id(), RequestId.toLong(partnerUserId, "userId"), date));
    }

    @ApiResponse(responseCode = "404", description = "RELATIONSHIP_NOT_FOUND")
    @GetMapping("/api/v1/people/{userId}/learned-facts")
    public PeopleLearnedFactsResponse learnedFacts(@AuthenticationPrincipal UserInfo userInfo,
                                                   @PathVariable("userId") String partnerUserId) {
        return PeopleLearnedFactsResponse.from(peopleService.learnedFacts(userInfo.id(), RequestId.toLong(partnerUserId, "userId")));
    }
}
