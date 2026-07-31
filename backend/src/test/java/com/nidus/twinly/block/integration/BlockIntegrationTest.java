package com.nidus.twinly.block.integration;

import com.nidus.twinly.block.entity.Block;
import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BlockIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    BlockRepository blockRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    @DisplayName("차단 성공: 실제 유저·JWT 인증·MockMvc·DB까지 관통하여 block 행이 생성된다")
    void block_success_end_to_end() throws Exception {
        // given: 실제 유저 2명(차단자/피차단자) 저장 (FK 때문에 둘 다 필요)
        User me = saveUser();
        User target = saveUser();

        // when: 차단자의 실제 액세스 토큰으로 차단 API 호출
        mockMvc.perform(put("/api/v1/blocks/{userId}", target.getId().toString())
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk());

        // then: DB에 실제로 차단 행이 생성됨
        assertThat(blockRepository.existsByUserIdAndBlockedUserId(me.getId(), target.getId())).isTrue();
    }

    @Test
    @DisplayName("차단 실패: 자기 자신을 차단하면 422와 CANNOT_BLOCK_SELF 코드를 반환하고 행이 생기지 않는다")
    void block_self_returns_422_end_to_end() throws Exception {
        // given: 실제 유저 1명 저장
        User me = saveUser();

        // when: 자기 자신을 대상으로 차단 API 호출
        var result = mockMvc.perform(put("/api/v1/blocks/{userId}", me.getId().toString())
                .header("Authorization", bearer(me.getId())));

        // then: 도메인 예외가 422 + CANNOT_BLOCK_SELF로 매핑되고 DB에도 행이 없음
        result.andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value(ErrorCode.CANNOT_BLOCK_SELF.name()));
        assertThat(blockRepository.existsByUserIdAndBlockedUserId(me.getId(), me.getId())).isFalse();
    }

    @Test
    @DisplayName("차단 해제 성공: 실제 block 행이 있을 때 호출하면 DB에서 행이 삭제된다")
    void unblock_success_end_to_end() throws Exception {
        // given: 실제 유저 2명과 이미 저장된 차단 행
        User me = saveUser();
        User target = saveUser();
        blockRepository.save(Block.create(me.getId(), target.getId()));
        flushAndClear();

        // when: 차단자의 실제 액세스 토큰으로 차단 해제 API 호출
        mockMvc.perform(delete("/api/v1/blocks/{userId}", target.getId().toString())
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk());

        // then: DB에서 실제로 차단 행이 사라짐
        flushAndClear();
        assertThat(blockRepository.existsByUserIdAndBlockedUserId(me.getId(), target.getId())).isFalse();
    }

    @Test
    @DisplayName("차단 해제 멱등: 차단 이력이 없어도 200으로 응답한다")
    void unblock_when_not_blocked_is_idempotent_end_to_end() throws Exception {
        // given: 차단 이력이 전혀 없는 실제 유저 2명
        User me = saveUser();
        User target = saveUser();

        // when: 차단한 적 없는 대상에 차단 해제 API 호출
        var result = mockMvc.perform(delete("/api/v1/blocks/{userId}", target.getId().toString())
                .header("Authorization", bearer(me.getId())));

        // then: 예외 없이 200 (멱등)
        result.andExpect(status().isOk());
    }

    @Test
    @DisplayName("차단 목록 조회 성공: 실제 조인 쿼리로 차단 대상의 id와 이름이 응답된다")
    void blockList_success_end_to_end() throws Exception {
        // given: 실제 유저 2명과 차단 행 1건 (이름은 familyName+givenName으로 조합됨)
        User me = saveUser();
        User target = saveUser();
        blockRepository.save(Block.create(me.getId(), target.getId()));
        flushAndClear();

        // when: 차단자의 실제 액세스 토큰으로 차단 목록 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/blocks")
                .header("Authorization", bearer(me.getId())));

        // then: 차단 대상 1건이 id(문자열)·이름과 함께 응답됨
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.blocks.length()").value(1))
                .andExpect(jsonPath("$.blocks[0].blockedUserId").value(target.getId().toString()))
                .andExpect(jsonPath("$.blocks[0].blockedUserName")
                        .value(target.getFamilyName() + target.getGivenName()));
    }

    @Test
    @DisplayName("차단 목록 조회: 차단이 한 건도 없으면 200과 빈 목록을 받는다")
    void blockList_when_no_block_returns_empty_list() throws Exception {
        // given: 차단한 적 없는 유저 (대다수 유저의 정상 상태)
        User me = saveUser();

        // when & then: 조립을 건너뛰고 빈 목록이 응답된다
        mockMvc.perform(get("/api/v1/blocks")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocks.length()").value(0));
    }

    @Test
    @DisplayName("차단 목록 조회: 탈퇴한 유저는 '탈퇴한 사용자'로 응답된다")
    void blockList_with_withdrawn_user_end_to_end() throws Exception {
        // given: 차단 대상이 탈퇴(deletedAt 세팅) 처리된 상태 — 세터가 없어 리플렉션으로 세팅
        User me = saveUser();
        User target = saveUser();
        ReflectionTestUtils.setField(target, "deletedAt", Instant.now());
        userRepository.save(target);
        blockRepository.save(Block.create(me.getId(), target.getId()));
        flushAndClear();

        // when: 차단 목록 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/blocks")
                .header("Authorization", bearer(me.getId())));

        // then: 이름이 '탈퇴한 사용자'로 마스킹되어 응답됨
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.blocks[0].blockedUserName").value("탈퇴한 사용자"));
    }

    @Test
    @DisplayName("차단 목록 조회 실패: 인증 헤더가 없으면 401을 반환한다")
    void blockList_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 차단 목록 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/blocks"));

        // then: 실제 인증 리졸버가 동작하여 401 반환
        result.andExpect(status().isUnauthorized());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("차단: 존재하지 않는 유저를 차단하면 404 USER_NOT_FOUND를 반환하고 차단 행이 생기지 않는다")
    void block_unknown_user_returns_404() throws Exception {
        // given: 실제 유저 1명 (차단 대상은 실재하지 않는 id)
        User me = saveUser();

        // when & then: 마스터 데이터(유저)에 없는 id이므로 404
        mockMvc.perform(put("/api/v1/blocks/{userId}", "99999999")
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        assertThat(blockRepository.findAllByUserId(me.getId())).isEmpty();
    }
}
