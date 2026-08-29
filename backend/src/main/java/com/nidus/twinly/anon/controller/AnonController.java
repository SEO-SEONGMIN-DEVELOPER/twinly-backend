package com.nidus.twinly.anon.controller;

import com.nidus.twinly.anon.dto.response.AnonStartResponse;
import com.nidus.twinly.anon.service.AnonService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "익명 세션")
@RestController
@RequiredArgsConstructor
public class AnonController {

    private final AnonService anonService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/v1/anon/start")
    public AnonStartResponse start() {
        return AnonStartResponse.from(anonService.start());
    }
}