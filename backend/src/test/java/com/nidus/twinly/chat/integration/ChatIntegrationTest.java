package com.nidus.twinly.chat.integration;

import com.nidus.twinly.chat.domain.ChatMessageType;
import com.nidus.twinly.chat.entity.Chat;
import com.nidus.twinly.chat.entity.ChatRoom;
import com.nidus.twinly.chat.entity.ChatRoomParticipation;
import com.nidus.twinly.chat.repository.ChatRepository;
import com.nidus.twinly.chat.repository.ChatRoomParticipationRepository;
import com.nidus.twinly.chat.repository.ChatRoomRepository;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatIntegrationTest extends AbstractIntegrationTest {

    /** 활성 시즌으로 넣을 시즌 id. 매칭의 season_id와 같아야 isCurrentSeason이 true가 된다. */
    private static final long CURRENT_SEASON_ID = 1L;

    @Autowired
    ChatRepository chatRepository;

    @Autowired
    ChatRoomRepository chatRoomRepository;

    @Autowired
    ChatRoomParticipationRepository chatRoomParticipationRepository;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    @DisplayName("메시지 전송 성공: 실제 유저·JWT 인증·MockMvc·DB까지 관통하여 chats 행이 생성된다")
    void sendMessage_success_end_to_end() throws Exception {
        // given: 시즌·유저 2명·매칭·채팅방·참여 정보를 실제 DB에 저장
        Fixture fixture = saveChatRoomFixture();

        // when: 발신자의 실제 액세스 토큰으로 메시지 전송 API 호출
        mockMvc.perform(post("/api/v1/chat/rooms/{roomId}/messages", fixture.roomId().toString())
                        .header("Authorization", bearer(fixture.me().getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hello\",\"clientMsgId\":\"client-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageId").isString())
                .andExpect(jsonPath("$.text").value("hello"))
                .andExpect(jsonPath("$.clientMsgId").value("client-1"));

        // then: DB에 실제로 채팅 행이 생성되고 수신자가 매칭 상대로 채워짐
        Chat saved = chatRepository.findBySenderUserIdAndClientMsgId(fixture.me().getId(), "client-1").orElseThrow();
        assertThat(saved.getRoomId()).isEqualTo(fixture.roomId());
        assertThat(saved.getReceiverUserId()).isEqualTo(fixture.partner().getId());
        assertThat(saved.getMessage()).isEqualTo("hello");
        assertThat(saved.getType()).isEqualTo(ChatMessageType.TEXT);
    }

    @Test
    @DisplayName("채팅방 목록 조회 성공: 실제 매칭·참여 정보·마지막 메시지·안읽음 수 집계 쿼리를 관통한다")
    void rooms_success_end_to_end() throws Exception {
        // given: 채팅방과 상대가 보낸(내가 아직 안 읽은) 메시지 1건을 실제 DB에 저장
        Fixture fixture = saveChatRoomFixture();
        chatRepository.save(Chat.create("client-partner-1", fixture.roomId(),
                fixture.partner().getId(), fixture.me().getId(), ChatMessageType.TEXT, "hi"));

        // when: 내 실제 액세스 토큰으로 채팅방 목록 API 호출
        mockMvc.perform(get("/api/v1/chat/rooms")
                        .header("Authorization", bearer(fixture.me().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rooms.length()").value(1))
                .andExpect(jsonPath("$.rooms[0].roomId").value(fixture.roomId().toString()))
                .andExpect(jsonPath("$.rooms[0].matchId").value(fixture.matchId().toString()))
                .andExpect(jsonPath("$.rooms[0].partner.userId").value(fixture.partner().getId().toString()))
                .andExpect(jsonPath("$.rooms[0].preview").value("hi"))
                .andExpect(jsonPath("$.rooms[0].messages.unreadCount").value(1))
                .andExpect(jsonPath("$.rooms[0].messages.lastMessage.text").value("hi"))
                .andExpect(jsonPath("$.rooms[0].isCurrentSeason").value(true));
    }

    @Test
    @DisplayName("채팅방 입장 성공: 응답의 입장 상태가 true가 되고 DB 참여 정보에 동의 시각이 저장된다")
    void enterRoom_success_end_to_end() throws Exception {
        // given: 아직 아무도 입장 동의하지 않은 채팅방
        Fixture fixture = saveChatRoomFixture();

        // when: 내 실제 액세스 토큰으로 채팅방 입장 API 호출
        mockMvc.perform(post("/api/v1/chat/rooms/{roomId}/enter", fixture.roomId().toString())
                        .header("Authorization", bearer(fixture.me().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(fixture.roomId().toString()))
                .andExpect(jsonPath("$.matchId").value(fixture.matchId().toString()))
                .andExpect(jsonPath("$.entryStatus.myEntryAgreed").value(true))
                .andExpect(jsonPath("$.entryStatus.partnerEntryAgreed").value(false))
                .andExpect(jsonPath("$.partner.userId").value(fixture.partner().getId().toString()))
                .andExpect(jsonPath("$.partner.intimacy").value(0))
                .andExpect(jsonPath("$.partner.relationshipSpecificType").value("RELATIONSHIP_SPECIFIC_TYPE_1"))
                .andExpect(jsonPath("$.isCurrentSeason").value(true));

        // then: 영속성 컨텍스트를 비우고 다시 읽어도 DB에 동의 시각이 남아 있음
        entityManager.flush();
        entityManager.clear();
        ChatRoomParticipation mine = chatRoomParticipationRepository
                .findByRoomIdAndUserId(fixture.roomId(), fixture.me().getId()).orElseThrow();
        assertThat(mine.getEntryAgreedAt()).isNotNull();
    }

    @Test
    @DisplayName("채팅방 상세 조회 성공: 실제 매칭·참여 정보·친밀도 조회를 관통하여 상대 정보가 응답된다")
    void roomDetail_success_end_to_end() throws Exception {
        // given: 상대만 입장 동의한 채팅방
        Fixture fixture = saveChatRoomFixture();
        ChatRoomParticipation partnerParticipation = chatRoomParticipationRepository
                .findByRoomIdAndUserId(fixture.roomId(), fixture.partner().getId()).orElseThrow();
        partnerParticipation.agree();
        flushAndClear();

        // when: 내 실제 액세스 토큰으로 채팅방 상세 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/chat/rooms/{roomId}", fixture.roomId().toString())
                .header("Authorization", bearer(fixture.me().getId())));

        // then: 입장 상태가 각각의 DB 상태대로, 상대 정보는 매칭 상대로 응답됨
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(fixture.roomId().toString()))
                .andExpect(jsonPath("$.matchId").value(fixture.matchId().toString()))
                .andExpect(jsonPath("$.entryStatus.myEntryAgreed").value(false))
                .andExpect(jsonPath("$.entryStatus.partnerEntryAgreed").value(true))
                .andExpect(jsonPath("$.partner.userId").value(fixture.partner().getId().toString()))
                .andExpect(jsonPath("$.partner.userName").value(fixture.partner().getNickname()))
                .andExpect(jsonPath("$.isCurrentSeason").value(true));
    }

    @Test
    @DisplayName("채팅방 상세 조회 실패: 매칭 당사자가 아니면 403과 NOT_MATCH_PARTICIPANT 코드를 반환한다")
    void roomDetail_when_not_participant_returns_403() throws Exception {
        // given: 채팅방과 무관한 제3의 실제 유저
        Fixture fixture = saveChatRoomFixture();
        User stranger = saveUser();

        // when: 제3자의 실제 액세스 토큰으로 채팅방 상세 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/chat/rooms/{roomId}", fixture.roomId().toString())
                .header("Authorization", bearer(stranger.getId())));

        // then: 도메인 예외가 403 + NOT_MATCH_PARTICIPANT로 매핑됨
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_MATCH_PARTICIPANT.name()));
    }

    @Test
    @DisplayName("채팅방 상세 조회 실패: 존재하지 않는 방이면 404와 ROOM_NOT_FOUND 코드를 반환한다")
    void roomDetail_when_room_not_found_returns_404() throws Exception {
        // given: 방 없이 실제 유저만 저장
        User me = saveUser();

        // when: 존재하지 않는 roomId로 상세 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/chat/rooms/{roomId}", "999999999")
                .header("Authorization", bearer(me.getId())));

        // then: 도메인 예외가 404 + ROOM_NOT_FOUND로 매핑됨
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.ROOM_NOT_FOUND.name()));
    }

    @Test
    @DisplayName("채팅방 숨김 성공: DB 참여 정보의 isHidden이 true가 되고 목록에서 사라진다")
    void hideRoom_success_end_to_end() throws Exception {
        // given: 숨기지 않은 채팅방
        Fixture fixture = saveChatRoomFixture();

        // when: 내 실제 액세스 토큰으로 채팅방 숨김 API 호출
        mockMvc.perform(post("/api/v1/chat/rooms/{roomId}/hide", fixture.roomId().toString())
                        .header("Authorization", bearer(fixture.me().getId())))
                .andExpect(status().isOk());

        // then: 영속성 컨텍스트를 비우고 다시 읽어도 isHidden이 true이고, 목록 조회에서도 제외됨
        flushAndClear();
        ChatRoomParticipation mine = chatRoomParticipationRepository
                .findByRoomIdAndUserId(fixture.roomId(), fixture.me().getId()).orElseThrow();
        assertThat(mine.getIsHidden()).isTrue();

        mockMvc.perform(get("/api/v1/chat/rooms")
                        .header("Authorization", bearer(fixture.me().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rooms.length()").value(0));
    }

    @Test
    @DisplayName("채팅방 나가기 성공: DB 참여 정보에 leftAt이 기록된다")
    void leaveRoom_success_end_to_end() throws Exception {
        // given: 나가지 않은 채팅방
        Fixture fixture = saveChatRoomFixture();

        // when: 내 실제 액세스 토큰으로 채팅방 나가기 API 호출
        mockMvc.perform(post("/api/v1/chat/rooms/{roomId}/leave", fixture.roomId().toString())
                        .header("Authorization", bearer(fixture.me().getId())))
                .andExpect(status().isOk());

        // then: 영속성 컨텍스트를 비우고 다시 읽어도 leftAt이 남아 있음
        flushAndClear();
        ChatRoomParticipation mine = chatRoomParticipationRepository
                .findByRoomIdAndUserId(fixture.roomId(), fixture.me().getId()).orElseThrow();
        assertThat(mine.getLeftAt()).isNotNull();
    }

    @Test
    @DisplayName("메시지 목록 조회 성공: 실제 커서 쿼리로 id 내림차순 페이지와 발신자 구분이 응답된다")
    void messages_success_end_to_end() throws Exception {
        // given: 내가 1건, 상대가 1건 보낸 메시지를 실제 DB에 저장
        Fixture fixture = saveChatRoomFixture();
        Chat mine = chatRepository.save(Chat.create("c-1", fixture.roomId(),
                fixture.me().getId(), fixture.partner().getId(), ChatMessageType.TEXT, "내 메시지"));
        Chat theirs = chatRepository.save(Chat.create("c-2", fixture.roomId(),
                fixture.partner().getId(), fixture.me().getId(), ChatMessageType.TEXT, "상대 메시지"));
        flushAndClear();

        // when: 내 실제 액세스 토큰으로 메시지 목록 API 호출 (limit 미지정 → 기본값)
        var result = mockMvc.perform(get("/api/v1/chat/rooms/{roomId}/messages", fixture.roomId().toString())
                .header("Authorization", bearer(fixture.me().getId())));

        // then: 최신(id 내림차순) 순으로 2건, 발신자 구분이 ME/THEM으로 응답되고 다음 페이지는 없음
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(fixture.roomId().toString()))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].messageId").value(theirs.getId().toString()))
                .andExpect(jsonPath("$.messages[0].senderType").value("them"))
                .andExpect(jsonPath("$.messages[1].messageId").value(mine.getId().toString()))
                .andExpect(jsonPath("$.messages[1].senderType").value("me"))
                .andExpect(jsonPath("$.page.hasMore").value(false))
                .andExpect(jsonPath("$.page.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName("메시지 목록 조회 성공: limit보다 많으면 hasMore=true와 nextCursor가 응답된다")
    void messages_with_limit_paging_end_to_end() throws Exception {
        // given: 메시지 2건을 저장하고 limit=1로 조회
        Fixture fixture = saveChatRoomFixture();
        chatRepository.save(Chat.create("c-1", fixture.roomId(),
                fixture.me().getId(), fixture.partner().getId(), ChatMessageType.TEXT, "첫 번째"));
        Chat second = chatRepository.save(Chat.create("c-2", fixture.roomId(),
                fixture.me().getId(), fixture.partner().getId(), ChatMessageType.TEXT, "두 번째"));
        flushAndClear();

        // when: limit=1로 메시지 목록 API 호출
        var result = mockMvc.perform(get("/api/v1/chat/rooms/{roomId}/messages", fixture.roomId().toString())
                .param("limit", "1")
                .header("Authorization", bearer(fixture.me().getId())));

        // then: 1건만 내려오고 hasMore=true, nextCursor는 마지막 항목의 id
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].messageId").value(second.getId().toString()))
                .andExpect(jsonPath("$.page.hasMore").value(true))
                .andExpect(jsonPath("$.page.nextCursor").value(second.getId().toString()));
    }

    @Test
    @DisplayName("읽음 처리 성공: DB 참여 정보의 lastReadMessageId가 전진하고 안읽음 수가 0이 된다")
    void readMessages_success_end_to_end() throws Exception {
        // given: 상대가 보낸(내가 안 읽은) 메시지 1건
        Fixture fixture = saveChatRoomFixture();
        Chat theirs = chatRepository.save(Chat.create("c-1", fixture.roomId(),
                fixture.partner().getId(), fixture.me().getId(), ChatMessageType.TEXT, "hi"));
        flushAndClear();

        // when: 내 실제 액세스 토큰으로 해당 메시지까지 읽음 처리 API 호출
        mockMvc.perform(post("/api/v1/chat/rooms/{roomId}/read", fixture.roomId().toString())
                        .header("Authorization", bearer(fixture.me().getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastMsgId\":\"" + theirs.getId() + "\"}"))
                .andExpect(status().isOk());

        // then: DB에 읽음 포인터가 저장되고, 목록 조회의 안읽음 수도 0으로 집계됨
        flushAndClear();
        ChatRoomParticipation mine = chatRoomParticipationRepository
                .findByRoomIdAndUserId(fixture.roomId(), fixture.me().getId()).orElseThrow();
        assertThat(mine.getLastReadMessageId()).isEqualTo(theirs.getId());

        mockMvc.perform(get("/api/v1/chat/rooms")
                        .header("Authorization", bearer(fixture.me().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rooms[0].messages.unreadCount").value(0));
    }

    @Test
    @DisplayName("읽음 처리 실패: 해당 방의 메시지가 아니면 422와 MESSAGE_NOT_IN_ROOM 코드를 반환한다")
    void readMessages_when_message_not_in_room_returns_422() throws Exception {
        // given: 채팅방은 있으나 존재하지 않는 메시지 id를 지정
        Fixture fixture = saveChatRoomFixture();

        // when: 다른 방의(존재하지 않는) 메시지 id로 읽음 처리 API 호출
        var result = mockMvc.perform(post("/api/v1/chat/rooms/{roomId}/read", fixture.roomId().toString())
                .header("Authorization", bearer(fixture.me().getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lastMsgId\":\"999999999\"}"));

        // then: 도메인 예외가 422 + MESSAGE_NOT_IN_ROOM으로 매핑됨
        result.andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value(ErrorCode.MESSAGE_NOT_IN_ROOM.name()));
    }

    @Test
    @DisplayName("메시지 목록 조회 실패: 인증 헤더가 없으면 401을 반환한다")
    void messages_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 메시지 목록 API 호출
        var result = mockMvc.perform(get("/api/v1/chat/rooms/{roomId}/messages", "1"));

        // then: 실제 인증 리졸버가 동작하여 401 반환
        result.andExpect(status().isUnauthorized());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    /** 시즌 → 유저 2명 → 매칭 → 채팅방 → 참여 정보 순으로 저장한다 (FK 순서 준수). */
    private Fixture saveChatRoomFixture() {
        saveCurrentSeason();

        User me = saveUser();
        User partner = saveUser();
        Long matchId = saveMatch(me.getId(), partner.getId());

        ChatRoom room = chatRoomRepository.save(ChatRoom.create(matchId));
        chatRoomParticipationRepository.save(ChatRoomParticipation.create(room.getId(), me.getId()));
        chatRoomParticipationRepository.save(ChatRoomParticipation.create(room.getId(), partner.getId()));

        return new Fixture(me, partner, matchId, room.getId());
    }

    /**
     * seasons/matches는 팩토리·세터가 없는 엔티티라 네이티브 INSERT로 픽스처를 만든다.
     * 시즌은 매칭의 season_id와 맞추기 위해 id를 명시하고, 현재 시즌이 되도록 is_active를 켠다. (테스트는 롤백되므로 매번 안전)
     */
    private void saveCurrentSeason() {
        entityManager.createNativeQuery("""
                        INSERT INTO seasons (id, started_at, ended_at, is_active, created_at)
                        VALUES (:id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 1, UTC_TIMESTAMP(6))
                        """)
                .setParameter("id", CURRENT_SEASON_ID)
                .executeUpdate();
    }

    private Long saveMatch(Long userId, Long partnerUserId) {
        // matches 테이블에 user_a_id < user_b_id 체크 제약이 있어 정렬해서 넣는다.
        long userAId = Math.min(userId, partnerUserId);
        long userBId = Math.max(userId, partnerUserId);

        entityManager.createNativeQuery("""
                        INSERT INTO matches (user_a_id, user_b_id, season_id, created_at)
                        VALUES (:userAId, :userBId, :seasonId, UTC_TIMESTAMP(6))
                        """)
                .setParameter("userAId", userAId)
                .setParameter("userBId", userBId)
                .setParameter("seasonId", CURRENT_SEASON_ID)
                .executeUpdate();

        Number matchId = (Number) entityManager.createNativeQuery("""
                        SELECT id FROM matches WHERE user_a_id = :userAId AND user_b_id = :userBId
                        """)
                .setParameter("userAId", userAId)
                .setParameter("userBId", userBId)
                .getSingleResult();

        return matchId.longValue();
    }

    private record Fixture(User me, User partner, Long matchId, Long roomId) {
    }
}
