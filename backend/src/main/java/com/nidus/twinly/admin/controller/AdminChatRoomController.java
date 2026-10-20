package com.nidus.twinly.admin.controller;

import com.nidus.twinly.admin.domain.ChatEntryState;
import com.nidus.twinly.admin.dto.response.AdminChatMessagePageResponse;
import com.nidus.twinly.admin.dto.response.AdminChatRoomResponse;
import com.nidus.twinly.admin.dto.response.AdminPageResponse;
import com.nidus.twinly.admin.service.AdminChatRoomService;
import com.nidus.twinly.common.web.RequestId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "어드민 콘솔 (관리자)")
@Validated
@RestController
@RequiredArgsConstructor
public class AdminChatRoomController {

    private final AdminChatRoomService adminChatRoomService;

    @Operation(summary = "채팅방 목록", description = "시드 회원이 있는 방은 빠진다. query가 숫자면 채팅방 번호나 회원 ID, 아니면 닉네임 일부로 찾는다.")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST")
    @GetMapping("/admin/chat-rooms")
    public AdminPageResponse<AdminChatRoomResponse> search(@RequestParam(required = false) String query,
                                                           @RequestParam(required = false) ChatEntryState entryState,
                                                           @RequestParam(defaultValue = "0") @Min(0) Integer page,
                                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size) {
        return AdminPageResponse.from(adminChatRoomService.search(query, entryState, page, size), AdminChatRoomResponse::from);
    }

    @Operation(summary = "채팅방 상세")
    @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND")
    @GetMapping("/admin/chat-rooms/{roomId}")
    public AdminChatRoomResponse room(@PathVariable String roomId) {
        return AdminChatRoomResponse.from(adminChatRoomService.room(RequestId.toLong(roomId, "roomId")));
    }

    @Operation(summary = "채팅방 메시지", description = "최신 메시지부터 size개. beforeId를 주면 그보다 이전 메시지를 준다.")
    @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND")
    @GetMapping("/admin/chat-rooms/{roomId}/messages")
    public AdminChatMessagePageResponse messages(@PathVariable String roomId,
                                                 @RequestParam(required = false) String beforeId,
                                                 @RequestParam(defaultValue = "50") @Min(1) @Max(200) Integer size) {
        return AdminChatMessagePageResponse.from(adminChatRoomService.messages(
                RequestId.toLong(roomId, "roomId"), RequestId.toLongOrNull(beforeId, "beforeId"), size));
    }
}
