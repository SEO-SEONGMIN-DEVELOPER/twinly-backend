package com.nidus.twinly.chat.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.chat.domain.ChatSenderType;
import com.nidus.twinly.chat.dto.command.ChatReadMessagesCommand;
import com.nidus.twinly.chat.dto.result.ChatReadMessagesResult;
import com.nidus.twinly.chat.dto.command.ChatSendMessageCommand;
import com.nidus.twinly.chat.dto.result.ChatMessageItemResult;
import com.nidus.twinly.chat.dto.result.ChatMessagesPageResult;
import com.nidus.twinly.chat.dto.result.ChatMessagesResult;
import com.nidus.twinly.chat.dto.result.ChatRoomDetailDisclosedFieldsResult;
import com.nidus.twinly.chat.dto.result.ChatRoomDetailPartnerResult;
import com.nidus.twinly.chat.dto.result.ChatRoomDetailResult;
import com.nidus.twinly.chat.dto.result.ChatRoomEntryStatusResult;
import com.nidus.twinly.chat.dto.result.ChatRoomLastMessageResult;
import com.nidus.twinly.chat.dto.result.ChatRoomMessagesResult;
import com.nidus.twinly.chat.dto.result.ChatRoomPartnerResult;
import com.nidus.twinly.chat.dto.result.ChatRoomResult;
import com.nidus.twinly.chat.dto.result.ChatRoomsResult;
import com.nidus.twinly.chat.dto.result.ChatSendMessageResult;
import com.nidus.twinly.chat.service.ChatService;
import com.nidus.twinly.relationship.domain.RelationshipSpecificType;
import com.nidus.twinly.user.dto.header.UserInfo;
import com.nidus.twinly.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
class ChatControllerUnitTest {

    private static final String AUTH_HEADER = "Bearer access-token";
    private static final Instant SENT_AT = Instant.parse("2026-07-26T00:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ChatService chatService;

    // ChatController가 직접 쓰진 않지만, WebMvcConfig가 두 resolver를 모두 주입받고
    // 각 resolver가 이 서비스에 의존하므로 슬라이스 기동에 필수.
    @MockitoBean
    UserService userService;

    @MockitoBean
    AnonService anonService;

    @BeforeEach
    void setUp() {
        given(userService.resolveByAccessToken(anyString()))
                .willReturn(new UserInfo(1L));
    }

    @Test
    @DisplayName("메시지 전송 시 text가 공백뿐이면 400을 반환하고 서비스를 호출하지 않는다")
    void sendMessage_with_blank_text_returns_400() throws Exception {
        // when: 공백만 있는 본문으로 메시지 전송 API 호출
        var result = mockMvc.perform(post("/api/v1/chat/rooms/{roomId}/messages", "10")
                .header("Authorization", AUTH_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"   \",\"clientMsgId\":\"client-1\"}"));

        // then: 400 INVALID_REQUEST 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        then(chatService).should(never()).sendMessage(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("메시지 목록 조회 시 limit이 허용 범위(1~100) 밖이면 400을 반환하고 서비스를 호출하지 않는다")
    void messages_with_out_of_range_limit_returns_400() throws Exception {
        // when & then: 0과 상한 초과 모두 입력 단계에서 막힌다
        for (String limit : List.of("0", "101")) {
            mockMvc.perform(get("/api/v1/chat/rooms/{roomId}/messages", "10")
                            .header("Authorization", AUTH_HEADER)
                            .param("limit", limit))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        then(chatService).should(never()).messages(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("메시지 전송 성공 시 200과 messageId를 문자열로 반환하고 인증 유저 id·roomId·커맨드로 서비스를 호출한다")
    void sendMessage_success() throws Exception {
        // given: 서비스가 저장된 메시지 결과를 반환
        given(chatService.sendMessage(eq(1L), eq(10L), any()))
                .willReturn(new ChatSendMessageResult(77L, "hello", SENT_AT, "client-1"));

        // when: 인증 상태로 메시지 전송 API 호출
        var result = mockMvc.perform(post("/api/v1/chat/rooms/{roomId}/messages", "10")
                .header("Authorization", AUTH_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"hello\",\"clientMsgId\":\"client-1\"}"));

        // then: 200 반환 + messageId가 문자열로 직렬화 + 인증 유저 id·roomId·커맨드로 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.messageId").value("77"))
                .andExpect(jsonPath("$.text").value("hello"))
                .andExpect(jsonPath("$.clientMsgId").value("client-1"));
        then(chatService).should().sendMessage(1L, 10L, new ChatSendMessageCommand("hello", "client-1"));
    }

    @Test
    @DisplayName("메시지 전송 시 text가 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void sendMessage_without_text_returns_400() throws Exception {
        // when: 필수값 text를 빠뜨린 본문으로 메시지 전송 API 호출
        var result = mockMvc.perform(post("/api/v1/chat/rooms/{roomId}/messages", "10")
                .header("Authorization", AUTH_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientMsgId\":\"client-1\"}"));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(chatService).should(never()).sendMessage(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("메시지 전송 시 경로 변수 roomId가 숫자가 아니면 400을 반환하고 서비스를 호출하지 않는다")
    void sendMessage_with_non_numeric_roomId_returns_400() throws Exception {
        // when: roomId를 숫자가 아닌 값으로 메시지 전송 API 호출
        var result = mockMvc.perform(post("/api/v1/chat/rooms/{roomId}/messages", "abc")
                .header("Authorization", AUTH_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"hello\",\"clientMsgId\":\"client-1\"}"));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(chatService).should(never()).sendMessage(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("인증 헤더가 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void sendMessage_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 메시지 전송 API 호출
        var result = mockMvc.perform(post("/api/v1/chat/rooms/{roomId}/messages", "10")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"hello\",\"clientMsgId\":\"client-1\"}"));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(chatService).should(never()).sendMessage(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("채팅방 목록 조회 시 서비스 결과를 응답 JSON으로 변환하고 id는 문자열로 직렬화한다")
    void rooms_success() throws Exception {
        // given: 서비스가 채팅방 1건을 반환
        given(chatService.rooms(1L)).willReturn(new ChatRoomsResult(List.of(new ChatRoomResult(
                10L,
                100L,
                new ChatRoomEntryStatusResult(true, false),
                new ChatRoomPartnerResult(2L, "partner", null, 30, false),
                "hello",
                new ChatRoomMessagesResult(3, new ChatRoomLastMessageResult("hello", SENT_AT)),
                null,
                null,
                true
        ))));

        // when: 채팅방 목록 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/chat/rooms")
                .header("Authorization", AUTH_HEADER));

        // then: 200 반환 + roomId/matchId/partner.userId가 문자열로 직렬화된 JSON 응답
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.rooms.length()").value(1))
                .andExpect(jsonPath("$.rooms[0].roomId").value("10"))
                .andExpect(jsonPath("$.rooms[0].matchId").value("100"))
                .andExpect(jsonPath("$.rooms[0].entryStatus.myEntryAgreed").value(true))
                .andExpect(jsonPath("$.rooms[0].partner.userId").value("2"))
                .andExpect(jsonPath("$.rooms[0].preview").value("hello"))
                .andExpect(jsonPath("$.rooms[0].messages.unreadCount").value(3))
                .andExpect(jsonPath("$.rooms[0].messages.lastMessage.text").value("hello"))
                .andExpect(jsonPath("$.rooms[0].isCurrentSeason").value(true));
        then(chatService).should().rooms(1L);
    }

    @Test
    @DisplayName("채팅방 상세 조회 시 서비스 결과를 응답 JSON으로 변환하고 인증 유저 id·roomId로 서비스를 호출한다")
    void roomDetail_success() throws Exception {
        // given: 서비스가 채팅방 상세를 반환
        given(chatService.roomDetail(1L, 10L)).willReturn(roomDetailResult(true, false));

        // when: 채팅방 상세 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/chat/rooms/{roomId}", "10")
                .header("Authorization", AUTH_HEADER));

        // then: 200 반환 + 상세 필드가 JSON으로 직렬화 + 인증 유저 id·roomId로 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value("10"))
                .andExpect(jsonPath("$.matchId").value("100"))
                .andExpect(jsonPath("$.partner.userId").value("2"))
                .andExpect(jsonPath("$.partner.intimacy").value(45))
                .andExpect(jsonPath("$.partner.relationshipSpecificType").value("RELATIONSHIP_SPECIFIC_TYPE_2"))
                .andExpect(jsonPath("$.partner.disclosedFields.affiliation").value("aff"))
                .andExpect(jsonPath("$.partner.disclosedFields.affiliationNumber").isEmpty())
                .andExpect(jsonPath("$.isCurrentSeason").value(true));
        then(chatService).should().roomDetail(1L, 10L);
    }

    @Test
    @DisplayName("채팅방 입장 성공 시 200과 상세 정보를 반환하고 인증 유저 id·roomId로 서비스를 호출한다")
    void enterRoom_success() throws Exception {
        // given: 서비스가 입장 동의가 반영된 상세를 반환
        given(chatService.enterRoom(1L, 10L)).willReturn(roomDetailResult(true, true));

        // when: 채팅방 입장 API 호출
        var result = mockMvc.perform(post("/api/v1/chat/rooms/{roomId}/enter", "10")
                .header("Authorization", AUTH_HEADER));

        // then: 200 반환 + 입장 상태가 JSON에 반영 + 인증 유저 id·roomId로 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value("10"))
                .andExpect(jsonPath("$.entryStatus.myEntryAgreed").value(true))
                .andExpect(jsonPath("$.entryStatus.partnerEntryAgreed").value(true));
        then(chatService).should().enterRoom(1L, 10L);
    }

    @Test
    @DisplayName("채팅방 숨김 성공 시 200을 반환하고 인증 유저 id·roomId로 서비스를 호출한다")
    void hideRoom_success() throws Exception {
        // when: 채팅방 숨김 API 호출
        var result = mockMvc.perform(post("/api/v1/chat/rooms/{roomId}/hide", "10")
                .header("Authorization", AUTH_HEADER));

        // then: 200 반환 + 인증 유저 id·roomId로 위임
        result.andExpect(status().isOk());
        then(chatService).should().hideRoom(1L, 10L);
    }

    @Test
    @DisplayName("메시지 목록 조회 시 cursor/limit 쿼리 파라미터를 그대로 서비스에 전달하고 응답 JSON으로 변환한다")
    void messages_with_cursor_and_limit() throws Exception {
        // given: 서비스가 메시지 1건과 다음 커서를 반환
        given(chatService.messages(eq(1L), eq(10L), any(), any()))
                .willReturn(new ChatMessagesResult(
                        10L,
                        List.of(new ChatMessageItemResult(77L, ChatSenderType.ME, "hello", SENT_AT, "client-1")),
                        66L,
                        new ChatMessagesPageResult(70L, true)
                ));

        // when: cursor와 limit을 붙여 메시지 목록 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/chat/rooms/{roomId}/messages", "10")
                .param("cursor", "55")
                .param("limit", "5")
                .header("Authorization", AUTH_HEADER));

        // then: 200 반환 + id는 문자열, senderType은 소문자로 직렬화 + cursor/limit이 그대로 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value("10"))
                .andExpect(jsonPath("$.messages[0].messageId").value("77"))
                .andExpect(jsonPath("$.messages[0].senderType").value("me"))
                .andExpect(jsonPath("$.messages[0].text").value("hello"))
                .andExpect(jsonPath("$.lastReadMessageId").value("66"))
                .andExpect(jsonPath("$.page.nextCursor").value("70"))
                .andExpect(jsonPath("$.page.hasMore").value(true));
        then(chatService).should().messages(1L, 10L, 55L, 5);
    }

    @Test
    @DisplayName("메시지 목록 조회 시 cursor/limit이 없으면 null로 서비스에 전달한다")
    void messages_without_cursor_and_limit() throws Exception {
        // given: 서비스가 빈 페이지를 반환
        given(chatService.messages(eq(1L), eq(10L), any(), any()))
                .willReturn(new ChatMessagesResult(10L, List.of(), null, new ChatMessagesPageResult(null, false)));

        // when: 쿼리 파라미터 없이 메시지 목록 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/chat/rooms/{roomId}/messages", "10")
                .header("Authorization", AUTH_HEADER));

        // then: 200 반환 + 상대가 아직 아무것도 읽지 않았으면 lastReadMessageId는 null + cursor/limit 모두 null로 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(0))
                .andExpect(jsonPath("$.lastReadMessageId").value(nullValue()))
                .andExpect(jsonPath("$.page.hasMore").value(false));
        then(chatService).should().messages(1L, 10L, null, null);
    }

    @Test
    @DisplayName("읽음 처리 성공 시 200을 반환하고 문자열로 받은 lastMsgId를 Long 커맨드로 변환해 서비스를 호출한다")
    void readMessages_success() throws Exception {
        // given: 서버가 확정한 읽음 포인터 (요청값과 다를 수 있다)
        given(chatService.readMessages(1L, 10L, new ChatReadMessagesCommand(77L)))
                .willReturn(new ChatReadMessagesResult(10L, 77L));

        // when: lastMsgId를 문자열로 담아 읽음 처리 API 호출
        var result = mockMvc.perform(post("/api/v1/chat/rooms/{roomId}/read", "10")
                .header("Authorization", AUTH_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lastMsgId\":\"77\"}"));

        // then: 200 + 확정된 포인터가 WebSocket 페이로드와 같은 필드명·문자열 형식으로 응답된다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value("10"))
                .andExpect(jsonPath("$.lastMsgId").value("77"));
        then(chatService).should().readMessages(1L, 10L, new ChatReadMessagesCommand(77L));
    }

    @Test
    @DisplayName("읽음 처리 시 lastMsgId가 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void readMessages_without_lastMsgId_returns_400() throws Exception {
        // when: 필수값 lastMsgId를 빠뜨린 본문으로 읽음 처리 API 호출
        var result = mockMvc.perform(post("/api/v1/chat/rooms/{roomId}/read", "10")
                .header("Authorization", AUTH_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(chatService).should(never()).readMessages(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("채팅방 나가기 성공 시 200을 반환하고 인증 유저 id·roomId로 서비스를 호출한다")
    void leaveRoom_success() throws Exception {
        // when: 채팅방 나가기 API 호출
        var result = mockMvc.perform(post("/api/v1/chat/rooms/{roomId}/leave", "10")
                .header("Authorization", AUTH_HEADER));

        // then: 200 반환 + 인증 유저 id·roomId로 위임
        result.andExpect(status().isOk());
        then(chatService).should().leaveRoom(1L, 10L);
    }

    private ChatRoomDetailResult roomDetailResult(boolean myEntryAgreed, boolean partnerEntryAgreed) {
        return new ChatRoomDetailResult(
                10L,
                100L,
                new ChatRoomEntryStatusResult(myEntryAgreed, partnerEntryAgreed),
                new ChatRoomDetailPartnerResult(
                        2L,
                        "partner",
                        null,
                        45,
                        RelationshipSpecificType.RELATIONSHIP_SPECIFIC_TYPE_2,
                        new ChatRoomDetailDisclosedFieldsResult("aff", null)
                ),
                null,
                null,
                true
        );
    }
}
