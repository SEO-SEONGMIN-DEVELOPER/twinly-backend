package com.nidus.twinly.people.controller;

import com.nidus.twinly.people.dto.response.PeopleResponse;
import com.nidus.twinly.people.service.PeopleService;
import com.nidus.twinly.user.annotation.CurrentUser;
import com.nidus.twinly.user.dto.header.UserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
