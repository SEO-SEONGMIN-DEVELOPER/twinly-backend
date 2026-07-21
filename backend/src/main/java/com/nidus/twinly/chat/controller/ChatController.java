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
import com.nidus.twinly.user.annotation.CurrentUser;
import com.nidus.twinly.user.dto.header.UserInfo;
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

    @PostMapping("/api/v1/chat/rooms/{roomId}/messages")
    public ChatSendMessageResponse sendMessage(@CurrentUser UserInfo userInfo,
                                               @PathVariable Long roomId,
                                               @Valid @RequestBody ChatSendMessageRequest request) {
        return ChatSendMessageResponse.from(chatService.sendMessage(userInfo.id(), roomId, ChatSendMessageCommand.from(request)));
    }

    @GetMapping("/api/v1/chat/rooms")
    public ChatRoomsResponse rooms(@CurrentUser UserInfo userInfo) {
        return ChatRoomsResponse.from(chatService.rooms(userInfo.id()));
    }

    @GetMapping("/api/v1/chat/rooms/{roomId}")
    public ChatRoomDetailResponse roomDetail(@CurrentUser UserInfo userInfo,
                                             @PathVariable Long roomId) {
        return ChatRoomDetailResponse.from(chatService.roomDetail(userInfo.id(), roomId));
    }

    @PostMapping("/api/v1/chat/rooms/{roomId}/enter")
    public ChatRoomDetailResponse enterRoom(@CurrentUser UserInfo userInfo,
                                        @PathVariable Long roomId) {
        return ChatRoomDetailResponse.from(chatService.enterRoom(userInfo.id(), roomId));
    }

    @PostMapping("/api/v1/chat/rooms/{roomId}/hide")
    public void hideRoom(@CurrentUser UserInfo userInfo,
                     @PathVariable Long roomId) {
        chatService.hideRoom(userInfo.id(), roomId);
    }

    @GetMapping("/api/v1/chat/rooms/{roomId}/messages")
    public ChatMessagesResponse messages(@CurrentUser UserInfo userInfo,
                                         @PathVariable Long roomId,
                                         @RequestParam(required = false) Long cursor,
                                         @RequestParam(required = false) Integer limit) {
        return ChatMessagesResponse.from(chatService.messages(userInfo.id(), roomId, cursor, limit));
    }

    @PostMapping("/api/v1/chat/rooms/{roomId}/read")
    public void readMessages(@CurrentUser UserInfo userInfo,
                     @PathVariable Long roomId,
                     @Valid @RequestBody ChatReadMessagesRequest request) {
        chatService.readMessages(userInfo.id(), roomId, ChatReadMessagesCommand.from(request));
    }

    @PostMapping("/api/v1/chat/rooms/{roomId}/leave")
    public void leaveRoom(@CurrentUser UserInfo userInfo,
                          @PathVariable Long roomId) {
        chatService.leaveRoom(userInfo.id(), roomId);
    }
}
