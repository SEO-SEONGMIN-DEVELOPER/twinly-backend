package com.nidus.twinly.people.controller;

import com.nidus.twinly.people.domain.IntimacyResolution;
import com.nidus.twinly.people.dto.response.PeopleEventResponse;
import com.nidus.twinly.people.dto.response.PeopleEventsResponse;
import com.nidus.twinly.people.dto.response.PeopleIntimacySeriesResponse;
import com.nidus.twinly.people.dto.response.PeopleProfileResponse;
import com.nidus.twinly.people.dto.response.PeopleResponse;
import com.nidus.twinly.people.service.PeopleService;
import com.nidus.twinly.user.annotation.CurrentUser;
import com.nidus.twinly.user.dto.header.UserInfo;
import lombok.RequiredArgsConstructor;
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
    public PeopleResponse people(@CurrentUser UserInfo userInfo,
                                    @RequestParam(required = false) Long cursor,
                                    @RequestParam(required = false) Integer limit) {
        return PeopleResponse.from(peopleService.people(userInfo.id(), cursor, limit));
    }

    @GetMapping("/api/v1/people/{userId}/profile")
    public PeopleProfileResponse profile(@CurrentUser UserInfo userInfo,
                                         @PathVariable("userId") Long partnerUserId) {
        return PeopleProfileResponse.from(peopleService.profile(userInfo.id(), partnerUserId));
    }

    @PutMapping("/api/v1/people/{userId}/favorites")
    public void favorites(@CurrentUser UserInfo userInfo,
                          @PathVariable("userId") Long partnerUserId) {
        peopleService.favorites(userInfo.id(), partnerUserId);
    }

    @DeleteMapping("/api/v1/people/{userId}/favorites")
    public void deleteFavorites(@CurrentUser UserInfo userInfo,
                                @PathVariable("userId") Long partnerUserId) {
        peopleService.deleteFavorites(userInfo.id(), partnerUserId);
    }

    @GetMapping("/api/v1/people/{userId}/intimacy-series")
    public PeopleIntimacySeriesResponse intimacySeries(@CurrentUser UserInfo userInfo,
                                                       @PathVariable("userId") Long partnerUserId,
                                                       @RequestParam LocalDate from,
                                                       @RequestParam LocalDate to,
                                                       @RequestParam IntimacyResolution resolution,
                                                       @RequestParam Integer maxPoints) {
        return PeopleIntimacySeriesResponse.from(peopleService.intimacySeries(userInfo.id(), partnerUserId, from, to, resolution, maxPoints));
    }

    @GetMapping("/api/v1/people/{userId}/events")
    public PeopleEventsResponse events(@CurrentUser UserInfo userInfo,
                                       @PathVariable("userId") Long partnerUserId,
                                       @RequestParam(required = false) LocalDate cursor,
                                       @RequestParam(required = false) Integer limit) {
        return PeopleEventsResponse.from(peopleService.events(userInfo.id(), partnerUserId, cursor, limit));
    }

    @GetMapping("/api/v1/people/{userId}/event/{date}")
    public PeopleEventResponse event(@CurrentUser UserInfo userInfo,
                                     @PathVariable("userId") Long partnerUserId,
                                     @PathVariable LocalDate date) {
        return PeopleEventResponse.from(peopleService.event(userInfo.id(), partnerUserId, date));
    }
}
