package com.nidus.twinly.block.integration;

import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BlockIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    BlockRepository blockRepository;

    @Test
    @DisplayName("차단 성공: 실제 유저·JWT 인증·MockMvc·DB까지 관통하여 block 행이 생성된다")
    void block_success_end_to_end() throws Exception {
        // given: 실제 유저 2명(차단자/피차단자) 저장 (FK 때문에 둘 다 필요)
        User me = saveUser();
        User target = saveUser();

        // when: 차단자의 실제 액세스 토큰으로 차단 API 호출
        mockMvc.perform(post("/api/v1/blocks/{userId}", target.getId().toString())
                        .header("Authorization", bearer(me.getId())))
                .andExpect(status().isOk());

        // then: DB에 실제로 차단 행이 생성됨
        assertThat(blockRepository.existsByUserIdAndBlockedUserId(me.getId(), target.getId())).isTrue();
    }
}
