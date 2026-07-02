package com.nidus.twinly.anon.controller;

import com.nidus.twinly.anon.dto.AnonStartResponse;
import com.nidus.twinly.anon.service.AnonService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AnonController {

    private final AnonService anonService;

    @PostMapping("/api/v1/anon/start")
    public AnonStartResponse start() {
        return AnonStartResponse.from(anonService.start());
    }
}