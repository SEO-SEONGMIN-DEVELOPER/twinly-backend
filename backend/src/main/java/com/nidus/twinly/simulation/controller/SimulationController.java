package com.nidus.twinly.simulation.controller;

import com.nidus.twinly.common.web.RequestId;
import com.nidus.twinly.simulation.dto.command.SimulationsCommand;
import com.nidus.twinly.simulation.dto.request.SimulationsRequest;
import com.nidus.twinly.simulation.dto.response.SimulationPersonaResponse;
import com.nidus.twinly.simulation.service.SimulationService;
import com.nidus.twinly.user.dto.response.UsersResponse;
import com.nidus.twinly.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "시뮬레이션")
@RestController
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;
    private final UserService userService;

    @Operation(summary = "시뮬레이션 참가 유저 목록 조회")
    @GetMapping("/internal/v1/users")
    public UsersResponse users(@RequestParam(required = false) String cursor,
                               @RequestParam(required = false) @Min(1) @Max(500) Integer limit) {
        return UsersResponse.from(userService.users(RequestId.toLongOrNull(cursor, "cursor"), limit));
    }

    @Operation(summary = "유저 시뮬레이션 결과 저장")
    @PostMapping("/internal/v1/users/{userId}/simulations")
    public void simulations(@PathVariable Long userId,
                            @Valid @RequestBody SimulationsRequest request) {
        simulationService.simulations(userId, SimulationsCommand.from(request));
    }

    @Operation(summary = "유저 페르소나 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "SIMULATION_ACCESS_REQUIRED"),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    })
    @GetMapping("/internal/v1/users/{userId}/persona")
    public SimulationPersonaResponse persona(@PathVariable("userId") String userId) {
        return SimulationPersonaResponse.from(simulationService.persona(RequestId.toLong(userId, "userId")));
    }
}
