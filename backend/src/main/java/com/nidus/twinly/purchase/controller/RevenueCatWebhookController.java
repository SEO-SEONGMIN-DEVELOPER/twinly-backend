package com.nidus.twinly.purchase.controller;

import com.nidus.twinly.purchase.dto.command.RevenueCatWebhookCommand;
import com.nidus.twinly.purchase.dto.request.RevenueCatWebhookRequest;
import com.nidus.twinly.purchase.service.PurchaseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "RevenueCat 웹훅")
@RestController
@RequiredArgsConstructor
public class RevenueCatWebhookController {

    private final PurchaseService purchaseService;

    @PostMapping("/webhook/v1/revenue-cat")
    public void revenueCat(@RequestBody RevenueCatWebhookRequest request) {
        purchaseService.receiveWebhook(RevenueCatWebhookCommand.from(request));
    }
}
