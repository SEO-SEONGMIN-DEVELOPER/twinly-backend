package com.nidus.twinly.block.controller;

import com.nidus.twinly.block.dto.response.BlockListResponse;
import com.nidus.twinly.block.service.BlockService;
import com.nidus.twinly.common.web.RequestId;
import com.nidus.twinly.user.dto.header.UserInfo;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND"),
            @ApiResponse(responseCode = "422", description = "CANNOT_BLOCK_SELF")
    })
    @PutMapping("/api/v1/blocks/{userId}")
    public void block(@AuthenticationPrincipal UserInfo userInfo,
                      @PathVariable String userId) {
        blockService.block(userInfo.id(), RequestId.toLong(userId, "userId"));
    }

    @DeleteMapping("/api/v1/blocks/{userId}")
    public void unblock(@AuthenticationPrincipal UserInfo userInfo,
                        @PathVariable String userId) {
        blockService.unblock(userInfo.id(), RequestId.toLong(userId, "userId"));
    }

    @GetMapping("/api/v1/blocks")
    public BlockListResponse blockList(@AuthenticationPrincipal UserInfo userInfo) {
        return BlockListResponse.from(blockService.blockList(userInfo.id()));
    }
}
