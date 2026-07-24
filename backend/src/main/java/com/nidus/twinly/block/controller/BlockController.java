package com.nidus.twinly.block.controller;

import com.nidus.twinly.block.dto.response.BlockListResponse;
import com.nidus.twinly.block.service.BlockService;
import com.nidus.twinly.common.web.RequestId;
import com.nidus.twinly.user.annotation.CurrentUser;
import com.nidus.twinly.user.dto.header.UserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    @PostMapping("/api/v1/blocks/{userId}")
    public void block(@CurrentUser UserInfo userInfo,
                      @PathVariable String userId) {
        blockService.block(userInfo.id(), RequestId.toLong(userId, "userId"));
    }

    @DeleteMapping("/api/v1/blocks/{userId}")
    public void unblock(@CurrentUser UserInfo userInfo,
                        @PathVariable String userId) {
        blockService.unblock(userInfo.id(), RequestId.toLong(userId, "userId"));
    }

    @GetMapping("/api/v1/blocks")
    public BlockListResponse blockList(@CurrentUser UserInfo userInfo) {
        return BlockListResponse.from(blockService.blockList(userInfo.id()));
    }
}
