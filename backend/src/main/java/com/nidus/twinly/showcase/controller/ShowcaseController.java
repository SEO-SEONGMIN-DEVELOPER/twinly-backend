package com.nidus.twinly.showcase.controller;

import com.nidus.twinly.showcase.dto.response.ShowcaseTodayResponse;
import com.nidus.twinly.showcase.dto.response.ShowcaseTodaySectorsResponse;
import com.nidus.twinly.showcase.dto.response.ShowcaseTodayV2Response;
import com.nidus.twinly.showcase.service.ShowcaseService;
import com.nidus.twinly.user.dto.header.UserInfo;
import com.nidus.twinly.world.dto.request.WorldTrackFormat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "쇼케이스")
@RestController
@RequiredArgsConstructor
public class ShowcaseController {

    private final ShowcaseService showcaseService;

    @Operation(summary = "오늘의 쇼케이스 조회")
    @ApiResponse(responseCode = "404", description = "SHOWCASE_TARGET_NOT_FOUND, USER_NOT_FOUND")
    @GetMapping("/api/v1/showcases/today")
    public ShowcaseTodayResponse today(@AuthenticationPrincipal UserInfo userInfo) {
        return ShowcaseTodayResponse.from(showcaseService.today(userInfo.id()));
    }

    @Operation(summary = "오늘의 쇼케이스 조회 v2")
    @ApiResponse(responseCode = "404", description = "SHOWCASE_TARGET_NOT_FOUND, USER_NOT_FOUND")
    @GetMapping("/api/v2/showcases/today")
    public ShowcaseTodayV2Response todayV2(@AuthenticationPrincipal UserInfo userInfo) {
        return ShowcaseTodayV2Response.from(showcaseService.today(userInfo.id()));
    }

    @Operation(summary = "오늘의 쇼케이스 대상 풀의 건물 화면 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "INVALID_REQUEST"),
            @ApiResponse(responseCode = "404", description = "SHOWCASE_TARGET_NOT_FOUND, WORLD_TRACK_NOT_FOUND")
    })
    @GetMapping("/api/v1/showcases/today/sectors/{sectorCode}")
    public ShowcaseTodaySectorsResponse todaySectors(@AuthenticationPrincipal UserInfo userInfo,
                                                     @PathVariable @Pattern(regexp = WorldTrackFormat.SECTOR_CODE) String sectorCode,
                                                     @RequestParam LocalDate date,
                                                     @Parameter(description = "정각 시작, +09:00 같은 오프셋 필수") @RequestParam String from,
                                                     @Parameter(description = "from 의 1시간 뒤") @RequestParam String to) {
        return ShowcaseTodaySectorsResponse.from(showcaseService.todaySectors(userInfo.id(), sectorCode, date, from, to));
    }
}
