package com.nidus.twinly.connection.controller;

import com.nidus.twinly.connection.dto.command.ConnectionDrainingCommand;
import com.nidus.twinly.connection.dto.request.ConnectionDrainingRequest;
import com.nidus.twinly.connection.service.ConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "커넥션 (관리자)")
@RestController
@RequiredArgsConstructor
public class ConnectionAdminController {

    private final ConnectionService connectionService;

    @Operation(summary = "배포 전 커넥션 종료 예고")
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN")
    @PostMapping("/admin/connection/draining")
    public void notifyDraining(@Valid @RequestBody ConnectionDrainingRequest request) {
        connectionService.notifyDraining(ConnectionDrainingCommand.from(request));
    }
}
