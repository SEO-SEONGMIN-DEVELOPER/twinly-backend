package com.nidus.twinly.chat.controller;

import jakarta.validation.Valid;
import com.nidus.twinly.chat.dto.command.ChatReadMessagesCommand;
import com.nidus.twinly.chat.dto.command.ChatSendMessageCommand;
import com.nidus.twinly.chat.dto.request.ChatReadMessagesRequest;
import com.nidus.twinly.chat.dto.request.ChatSendMessageRequest;
import com.nidus.twinly.chat.dto.response.ChatMessagesResponse;
import com.nidus.twinly.chat.dto.response.ChatRoomDetailResponse;
import com.nidus.twinly.chat.dto.response.ChatRoomsResponse;
import com.nidus.twinly.chat.dto.response.ChatSendMessageResponse;
import com.nidus.twinly.chat.service.ChatService;
import com.nidus.twinly.common.web.RequestId;
import com.nidus.twinly.user.annotation.CurrentUser;
import com.nidus.twinly.user.dto.header.UserInfo;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "NOT_MATCH_PARTICIPANT"),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND, MATCH_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "CLIENT_MSG_ID_CONFLICT"),
            @ApiResponse(responseCode = "422", description = "MESSAGE_LENGTH_EXCEEDED")
    })
    @PostMapping("/api/v1/chat/rooms/{roomId}/messages")
    public ChatSendMessageResponse sendMessage(@CurrentUser UserInfo userInfo,
                                               @PathVariable String roomId,
                                               @Valid @RequestBody ChatSendMessageRequest request) {
        return ChatSendMessageResponse.from(chatService.sendMessage(userInfo.id(), RequestId.toLong(roomId, "roomId"), ChatSendMessageCommand.from(request)));
    }

    @ApiResponse(responseCode = "403", description = "NOT_MATCH_PARTICIPANT")
    @GetMapping("/api/v1/chat/rooms")
    public ChatRoomsResponse rooms(@CurrentUser UserInfo userInfo) {
        return ChatRoomsResponse.from(chatService.rooms(userInfo.id()));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "NOT_MATCH_PARTICIPANT"),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND, MATCH_NOT_FOUND, USER_NOT_FOUND")
    })
    @GetMapping("/api/v1/chat/rooms/{roomId}")
    public ChatRoomDetailResponse roomDetail(@CurrentUser UserInfo userInfo,
                                             @PathVariable String roomId) {
        return ChatRoomDetailResponse.from(chatService.roomDetail(userInfo.id(), RequestId.toLong(roomId, "roomId")));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "NOT_MATCH_PARTICIPANT"),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND, MATCH_NOT_FOUND, USER_NOT_FOUND")
    })
    @PostMapping("/api/v1/chat/rooms/{roomId}/enter")
    public ChatRoomDetailResponse enterRoom(@CurrentUser UserInfo userInfo,
                                        @PathVariable String roomId) {
        return ChatRoomDetailResponse.from(chatService.enterRoom(userInfo.id(), RequestId.toLong(roomId, "roomId")));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "NOT_MATCH_PARTICIPANT"),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND, MATCH_NOT_FOUND")
    })
    @PostMapping("/api/v1/chat/rooms/{roomId}/hide")
    public void hideRoom(@CurrentUser UserInfo userInfo,
                     @PathVariable String roomId) {
        chatService.hideRoom(userInfo.id(), RequestId.toLong(roomId, "roomId"));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "NOT_MATCH_PARTICIPANT"),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND, MATCH_NOT_FOUND")
    })
    @GetMapping("/api/v1/chat/rooms/{roomId}/messages")
    public ChatMessagesResponse messages(@CurrentUser UserInfo userInfo,
                                         @PathVariable String roomId,
                                         @RequestParam(required = false) String cursor,
                                         @RequestParam(required = false) @Min(1) @Max(100) Integer limit) {
        return ChatMessagesResponse.from(chatService.messages(userInfo.id(), RequestId.toLong(roomId, "roomId"), RequestId.toLongOrNull(cursor, "cursor"), limit));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "NOT_MATCH_PARTICIPANT"),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND, MATCH_NOT_FOUND, CHAT_PARTICIPATION_NOT_FOUND"),
            @ApiResponse(responseCode = "422", description = "MESSAGE_NOT_IN_ROOM")
    })
    @PostMapping("/api/v1/chat/rooms/{roomId}/read")
    public void readMessages(@CurrentUser UserInfo userInfo,
                     @PathVariable String roomId,
                     @Valid @RequestBody ChatReadMessagesRequest request) {
        chatService.readMessages(userInfo.id(), RequestId.toLong(roomId, "roomId"), ChatReadMessagesCommand.from(request));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "NOT_MATCH_PARTICIPANT"),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND, MATCH_NOT_FOUND")
    })
    @PostMapping("/api/v1/chat/rooms/{roomId}/leave")
    public void leaveRoom(@CurrentUser UserInfo userInfo,
                          @PathVariable String roomId) {
        chatService.leaveRoom(userInfo.id(), RequestId.toLong(roomId, "roomId"));
    }
}
