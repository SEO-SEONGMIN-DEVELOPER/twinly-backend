package com.nidus.twinly.anon.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import com.nidus.twinly.anon.dto.AnonStartResponse;
import com.nidus.twinly.anon.service.AnonService;

@RestController
@RequiredArgsConstructor
public class AnonController {

    private final AnonService anonService;

    @PostMapping("/api/v1/anon/start")
    public AnonStartResponse start() {
        return anonService.start();
    }
}