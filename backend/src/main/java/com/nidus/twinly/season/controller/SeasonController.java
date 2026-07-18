package com.nidus.twinly.season.controller;

import com.nidus.twinly.season.dto.response.SeasonParticipationResponse;
import com.nidus.twinly.season.service.SeasonService;
import com.nidus.twinly.user.annotation.CurrentUser;
import com.nidus.twinly.user.dto.header.UserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SeasonController {

    private final SeasonService seasonService;

    @PostMapping("/api/v1/season/participations")
    public void participateIn(@CurrentUser UserInfo userInfo) {
        seasonService.participateIn(userInfo.id());
    }

    @GetMapping("/api/v1/season/participation")
    public SeasonParticipationResponse participation(@CurrentUser UserInfo userInfo) {
        return SeasonParticipationResponse.from(seasonService.participation(userInfo.id()));
    }
}
