package com.nidus.twinly.subscription.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class RevenueCatWebhookController {

    @PostMapping("/webhook/v1/revenue-cat")
    public void revenueCat(@RequestBody String payload) {
        log.info("RevenueCat webhook payload: {}", payload);
    }
}
