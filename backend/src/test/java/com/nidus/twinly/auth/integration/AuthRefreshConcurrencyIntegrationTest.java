package com.nidus.twinly.auth.integration;

import com.nidus.twinly.auth.dto.result.AuthTokenResult;
import com.nidus.twinly.auth.entity.RefreshToken;
import com.nidus.twinly.auth.repository.RefreshTokenRepository;
import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 같은 리프레시 토큰을 든 두 요청이 동시에 들어오는 경합을 재현한다.
 * 행 잠금은 서로 다른 실제 트랜잭션 사이에서만 동작하므로, 테스트 트랜잭션(롤백)을 끄고
 * 두 스레드가 각자의 트랜잭션으로 같은 행을 두고 경쟁하게 한다.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthRefreshConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final int ROUNDS = 20;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    BlindIndexHasher blindIndexHasher;

    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // 롤백이 없으므로 직접 지운다. 순서는 FK 의존의 역방향(refresh_tokens → users)
        refreshTokenRepository.deleteAll();
        createdUserIds.forEach(userRepository::deleteById);
        createdUserIds.clear();
    }

    @Test
    @DisplayName("토큰 재발급 동시 요청: 같은 리프레시 토큰으로 두 요청이 동시에 오면 하나만 성공하고 나머지는 401 REFRESH_TOKEN_ALREADY_REVOKED 로 거절되며 500 은 나지 않는다")
    void refresh_concurrent_same_token_only_one_succeeds() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                // given: 실제 유저와, 그 유저에게 발급된 리프레시 토큰 행 하나를 커밋된 상태로 저장
                User user = saveUser();
                createdUserIds.add(user.getId());
                AuthTokenResult issued = jwtService.generateAuthTokenResult(user.getId());
                String issuedHash = blindIndexHasher.hash(issued.refreshToken());
                refreshTokenRepository.save(RefreshToken.create(user.getId(), issuedHash, issued.refreshExpiresAt()));

                // when: 두 스레드가 출발 신호를 기다렸다가 같은 토큰으로 동시에 재발급 API 호출
                CountDownLatch start = new CountDownLatch(1);
                List<Future<MvcResult>> futures = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    futures.add(executor.submit(() -> {
                        start.await();
                        return mockMvc.perform(post("/api/v1/auth/refresh")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                {"refreshToken":"%s"}
                                                """.formatted(issued.refreshToken())))
                                .andReturn();
                    }));
                }
                start.countDown();

                List<Integer> statuses = new ArrayList<>();
                List<String> rejectedCodes = new ArrayList<>();
                for (Future<MvcResult> future : futures) {
                    MvcResult result = future.get();
                    int status = result.getResponse().getStatus();
                    statuses.add(status);
                    if (status != 200) {
                        rejectedCodes.add(JsonPath.read(result.getResponse().getContentAsString(), "$.code"));
                    }
                }

                // then: 정확히 하나만 200, 나머지는 401 + REFRESH_TOKEN_ALREADY_REVOKED. 500 은 한 번도 없어야 한다
                assertThat(statuses).as("round %d statuses", round).containsExactlyInAnyOrder(200, 401);
                assertThat(rejectedCodes).as("round %d rejected codes", round)
                        .containsExactly(ErrorCode.REFRESH_TOKEN_ALREADY_REVOKED.name());

                // then: 기존 토큰 행은 회전되어 사라지고, 해당 유저의 새 토큰 행은 정확히 하나만 남는다
                List<RefreshToken> remaining = refreshTokenRepository.findAll().stream()
                        .filter(token -> token.getUserId().equals(user.getId()))
                        .toList();
                assertThat(remaining).extracting(RefreshToken::getTokenHash).doesNotContain(issuedHash);
                assertThat(remaining).hasSize(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
