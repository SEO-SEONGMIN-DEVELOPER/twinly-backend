package com.nidus.twinly.chat.integration;

import com.nidus.twinly.chat.entity.ChatRoom;
import com.nidus.twinly.chat.entity.ChatRoomParticipation;
import com.nidus.twinly.chat.repository.ChatRoomParticipationRepository;
import com.nidus.twinly.chat.repository.ChatRoomRepository;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공통점 API 는 AI 호출 동안 DB 커넥션을 잡지 않도록 트랜잭션 없이(NOT_SUPPORTED) 돈다.
 * 테스트 트랜잭션 안에 넣은 커밋 전 픽스처는 서비스가 쓰는 다른 커넥션에서 보이지 않으므로,
 * 테스트 트랜잭션(롤백)을 끄고 픽스처를 커밋한 뒤 직접 지운다.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChatCommonPointIntegrationTest extends AbstractIntegrationTest {

    private static final long CURRENT_SEASON_ID = 1L;
    private static final String GENERATED = "두 사람은 주말마다 등산을 즐겨요.";

    @Autowired
    ChatRoomRepository chatRoomRepository;

    @Autowired
    ChatRoomParticipationRepository chatRoomParticipationRepository;

    @Autowired
    PersonaElementRepository personaElementRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdMatchIds = new ArrayList<>();
    private boolean seasonCreated;

    @AfterEach
    void cleanUp() {
        // 롤백이 없으므로 직접 지운다. 순서는 FK 의존의 역방향(참여 → 방 → 매칭 → 페르소나 → 유저 → 시즌)
        for (Long matchId : createdMatchIds) {
            jdbcTemplate.update("DELETE p FROM chat_room_participations p JOIN chat_rooms r ON r.id = p.room_id WHERE r.match_id = ?", matchId);
            jdbcTemplate.update("DELETE FROM chat_rooms WHERE match_id = ?", matchId);
            jdbcTemplate.update("DELETE FROM matches WHERE id = ?", matchId);
        }
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM persona_elements WHERE user_id = ?", userId);
            userRepository.deleteById(userId);
        }
        if (seasonCreated) {
            jdbcTemplate.update("DELETE FROM seasons WHERE id = ?", CURRENT_SEASON_ID);
        }
        createdMatchIds.clear();
        createdUserIds.clear();
        seasonCreated = false;
    }

    @Test
    @DisplayName("공통점 생성 성공: 트랜잭션 없이 AI 를 호출하고, 비어 있던 방에 생성 결과가 실제로 저장된다")
    void commonPoint_generates_and_stores_end_to_end() throws Exception {
        // given: 양쪽 입장 동의를 마친 방과 '등산' 공통점이 있는 두 사람의 페르소나를 커밋, AI 는 추출·문장 생성 응답으로 스텁
        Fixture fixture = saveAgreedRoomWithPersonas();
        stubBedrock(null);

        // when: 내 실제 액세스 토큰으로 공통점 API 호출
        mockMvc.perform(get("/api/v1/chat/rooms/{roomId}/common-point", fixture.roomId().toString())
                        .header("Authorization", bearer(fixture.me().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(GENERATED));

        // then: 조건부 UPDATE 가 실제 MySQL 에 반영되어 방에 공통점이 저장됨
        assertThat(chatRoomRepository.findCommonPointById(fixture.roomId())).contains(GENERATED);
    }

    @Test
    @DisplayName("공통점 재조회: 이미 저장된 방은 AI 를 다시 부르지 않고 저장된 문구를 그대로 돌려준다")
    void commonPoint_returns_stored_without_ai_call() throws Exception {
        // given: 이미 공통점이 저장된 방
        Fixture fixture = saveAgreedRoomWithPersonas();
        jdbcTemplate.update("UPDATE chat_rooms SET common_point = ? WHERE id = ?", "저장된 공통점", fixture.roomId());

        // when: 상대의 실제 액세스 토큰으로 공통점 API 호출
        mockMvc.perform(get("/api/v1/chat/rooms/{roomId}/common-point", fixture.roomId().toString())
                        .header("Authorization", bearer(fixture.partner().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("저장된 공통점"));

        // then: AI 호출이 없음
        then(bedrockService).should(never()).converse(anyString());
    }

    @Test
    @DisplayName("공통점 동시 생성: AI 응답을 기다리는 사이 다른 요청이 먼저 저장하면 덮어쓰지 않고 먼저 저장된 문구를 돌려준다")
    void commonPoint_keeps_first_saved_when_concurrently_generated() throws Exception {
        // given: 문장 생성 중에 상대 요청이 먼저 저장을 끝낸 상황을, AI 스텁 안에서 커밋된 UPDATE 로 재현
        Fixture fixture = saveAgreedRoomWithPersonas();
        stubBedrock(() -> jdbcTemplate.update("UPDATE chat_rooms SET common_point = ? WHERE id = ?", "상대가 먼저 저장한 공통점", fixture.roomId()));

        // when: 내 실제 액세스 토큰으로 공통점 API 호출
        mockMvc.perform(get("/api/v1/chat/rooms/{roomId}/common-point", fixture.roomId().toString())
                        .header("Authorization", bearer(fixture.me().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("상대가 먼저 저장한 공통점"));

        // then: 먼저 저장된 값이 그대로 남아 두 사람이 같은 문구를 본다
        assertThat(chatRoomRepository.findCommonPointById(fixture.roomId())).contains("상대가 먼저 저장한 공통점");
    }

    private void stubBedrock(Runnable duringSentence) {
        String extraction = """
                {"shared":[{"content":"등산을 즐김","keyword":"등산","evidenceA":"주말마다 북한산에 등산을 가요","evidenceB":"등산 동호회에서 주말마다 산에 가요"}],"contrast":null}
                """;
        given(bedrockService.converse(anyString())).willAnswer(invocation -> {
            String prompt = invocation.getArgument(0);
            if (prompt.contains("JSON으로만")) {
                return extraction;
            }
            if (duringSentence != null) {
                duringSentence.run();
            }
            return GENERATED;
        });
    }

    /** 시즌 → 유저 2명 → 페르소나 → 매칭 → 채팅방 → 참여(양쪽 동의) 순으로 커밋한다 (FK 순서 준수). */
    private Fixture saveAgreedRoomWithPersonas() {
        seasonCreated = jdbcTemplate.update("""
                INSERT IGNORE INTO seasons (id, started_at, ended_at, is_active, created_at)
                VALUES (?, UTC_TIMESTAMP(6) - INTERVAL 30 DAY, UTC_TIMESTAMP(6) + INTERVAL 30 DAY, 1, UTC_TIMESTAMP(6))
                """, CURRENT_SEASON_ID) == 1;

        User me = saveUser();
        User partner = saveUser();
        createdUserIds.add(me.getId());
        createdUserIds.add(partner.getId());

        Instant now = Instant.now();
        personaElementRepository.save(PersonaElement.create(me.getId(), PersonaDimension.INTEREST, "주말마다 북한산에 등산을 가요", now));
        personaElementRepository.save(PersonaElement.create(partner.getId(), PersonaDimension.INTEREST, "등산 동호회에서 주말마다 산에 가요", now));

        long userAId = Math.min(me.getId(), partner.getId());
        long userBId = Math.max(me.getId(), partner.getId());
        jdbcTemplate.update("""
                INSERT INTO matches (user_a_id, user_b_id, season_id, created_at)
                VALUES (?, ?, ?, UTC_TIMESTAMP(6))
                """, userAId, userBId, CURRENT_SEASON_ID);
        Long matchId = jdbcTemplate.queryForObject(
                "SELECT id FROM matches WHERE user_a_id = ? AND user_b_id = ?", Long.class, userAId, userBId);
        createdMatchIds.add(matchId);

        ChatRoom room = chatRoomRepository.save(ChatRoom.create(matchId));
        ChatRoomParticipation mine = ChatRoomParticipation.create(room.getId(), me.getId());
        ChatRoomParticipation theirs = ChatRoomParticipation.create(room.getId(), partner.getId());
        mine.agree();
        theirs.agree();
        chatRoomParticipationRepository.save(mine);
        chatRoomParticipationRepository.save(theirs);

        return new Fixture(me, partner, room.getId());
    }

    private record Fixture(User me, User partner, Long roomId) {
    }
}
