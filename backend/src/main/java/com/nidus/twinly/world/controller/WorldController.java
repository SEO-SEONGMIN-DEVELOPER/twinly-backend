package com.nidus.twinly.world.controller;

import com.nidus.twinly.user.dto.header.UserInfo;
import com.nidus.twinly.world.dto.request.WorldTrackFormat;
import com.nidus.twinly.world.dto.response.WorldSectorsResponse;
import com.nidus.twinly.world.service.WorldService;
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

@Tag(name = "세계")
@RestController
@RequiredArgsConstructor
public class WorldController {

    private final WorldService worldService;

    @Operation(summary = "건물 화면 조회 (이번 시즌 참여자)")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "INVALID_REQUEST"),
            @ApiResponse(responseCode = "403", description = "WORLD_PARTICIPATION_REQUIRED"),
            @ApiResponse(responseCode = "404", description = "WORLD_TRACK_NOT_FOUND")
    })
    @GetMapping("/api/v1/world/sectors/{sectorCode}")
    public WorldSectorsResponse sectors(@AuthenticationPrincipal UserInfo userInfo,
                                        @PathVariable @Pattern(regexp = WorldTrackFormat.SECTOR_CODE) String sectorCode,
                                        @RequestParam LocalDate date,
                                        @Parameter(description = "정각 시작, +09:00 같은 오프셋 필수") @RequestParam String from,
                                        @Parameter(description = "from 의 1시간 뒤") @RequestParam String to) {
        return WorldSectorsResponse.from(worldService.sectors(userInfo.id(), sectorCode, date, from, to));
    }
}
