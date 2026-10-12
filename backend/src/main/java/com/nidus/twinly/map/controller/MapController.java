package com.nidus.twinly.map.controller;

import com.nidus.twinly.map.dto.response.MapTwinsResponse;
import com.nidus.twinly.map.service.MapService;
import com.nidus.twinly.user.dto.header.UserInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "지도")
@RestController
@RequiredArgsConstructor
public class MapController {

    private final MapService mapService;

    @Operation(summary = "지도에 그릴 다른 트윈 동선 조회")
    @GetMapping("/api/v1/map/twins")
    public MapTwinsResponse twins(@AuthenticationPrincipal UserInfo userInfo) {
        return MapTwinsResponse.from(mapService.twins(userInfo.id()));
    }
}
