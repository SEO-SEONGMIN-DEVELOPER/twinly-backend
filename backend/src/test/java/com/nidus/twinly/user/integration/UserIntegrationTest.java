package com.nidus.twinly.user.integration;

import com.nidus.twinly.legal.entity.Agreement;
import com.nidus.twinly.legal.repository.PolicyNameRepository;
import com.nidus.twinly.legal.repository.PolicyRepository;
import com.nidus.twinly.purchase.entity.UserEntitlement;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.purchase.repository.UserEntitlementRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    UserEntitlementRepository userEntitlementRepository;

    @Autowired
    PolicyNameRepository policyNameRepository;

    @Autowired
    PolicyRepository policyRepository;

    @Test
    @DisplayName("유저 목록 조회: 커서와 같은 id는 제외하고 그보다 큰 유저만 오름차순으로 반환한다")
    void users_excludes_cursor_itself_end_to_end() throws Exception {
        // given: 실제 유저 3명을 DB에 저장하고, 첫 번째 유저를 커서로 사용
        User first = saveSubscribedUser();
        User second = saveSubscribedUser();
        User third = saveSubscribedUser();
        userRepository.flush();

        // when: 첫 번째 유저의 id를 커서로 유저 목록 API 호출
        var result = mockMvc.perform(get("/internal/v1/users")
                .param("cursor", first.getId().toString())
                .param("limit", "10"));

        // then: 커서 유저는 빠지고 이후 유저만 오름차순 + 마지막 페이지로 표시
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.userIds", contains(second.getId().toString(), third.getId().toString())))
                .andExpect(jsonPath("$.page.hasMore", is(false)))
                .andExpect(jsonPath("$.page.nextCursor", is(nullValue())));
    }

    @Test
    @DisplayName("유저 목록 조회: nextCursor로 이어서 호출하면 누락·중복 없이 다음 페이지를 받는다")
    void users_paging_end_to_end() throws Exception {
        // given: 실제 유저 3명을 DB에 저장하고, 첫 번째 유저를 커서로 사용
        User first = saveSubscribedUser();
        User second = saveSubscribedUser();
        User third = saveSubscribedUser();
        userRepository.flush();

        // when: limit 1로 첫 페이지를 조회
        var firstPage = mockMvc.perform(get("/internal/v1/users")
                .param("cursor", first.getId().toString())
                .param("limit", "1"));

        // then: 한 건만 반환하고 다음 커서를 내려준다
        firstPage.andExpect(status().isOk())
                .andExpect(jsonPath("$.userIds", contains(second.getId().toString())))
                .andExpect(jsonPath("$.page.hasMore", is(true)))
                .andExpect(jsonPath("$.page.nextCursor", is(second.getId().toString())));

        // when: 내려받은 nextCursor로 다음 페이지를 조회
        var secondPage = mockMvc.perform(get("/internal/v1/users")
                .param("cursor", second.getId().toString())
                .param("limit", "1"));

        // then: 앞 페이지와 겹치지 않는 다음 유저가 반환되고 페이징이 끝난다
        secondPage.andExpect(status().isOk())
                .andExpect(jsonPath("$.userIds", contains(third.getId().toString())))
                .andExpect(jsonPath("$.page.hasMore", is(false)))
                .andExpect(jsonPath("$.page.nextCursor", is(nullValue())));
    }

    @Test
    @DisplayName("유저 목록 조회: 파기된 유저는 목록에서 제외된다")
    void users_excludes_deleted_end_to_end() throws Exception {
        // given: 유저 3명 중 가운데 유저를 파기 처리
        User first = saveSubscribedUser();
        User deleted = saveSubscribedUser();
        User alive = saveSubscribedUser();
        deleted.delete();
        userRepository.flush();

        // when: 첫 번째 유저의 id를 커서로 유저 목록 API 호출
        var result = mockMvc.perform(get("/internal/v1/users")
                .param("cursor", first.getId().toString())
                .param("limit", "10"));

        // then: 파기된 유저는 빠지고 살아 있는 유저만 반환
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.userIds", hasSize(1)))
                .andExpect(jsonPath("$.userIds", contains(alive.getId().toString())));
    }

    @Test
    @DisplayName("유저 목록 조회: 탈퇴 유예 기간 중인 유저도 목록에서 제외된다")
    void users_excludes_withdrawal_requested_end_to_end() throws Exception {
        // given: 유저 3명 중 가운데 유저가 탈퇴를 신청해 복구 가능 기간에 있다
        User first = saveSubscribedUser();
        User pending = saveSubscribedUser();
        User alive = saveSubscribedUser();
        pending.requestWithdrawal(Duration.ofDays(15));
        userRepository.flush();

        // when: 첫 번째 유저의 id를 커서로 유저 목록 API 호출
        var result = mockMvc.perform(get("/internal/v1/users")
                .param("cursor", first.getId().toString())
                .param("limit", "10"));

        // then: 파기 전이어도 탈퇴 신청한 유저는 AI 서버에 넘기지 않는다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.userIds", hasSize(1)))
                .andExpect(jsonPath("$.userIds", contains(alive.getId().toString())));
    }

    @Test
    @DisplayName("유저 목록 조회: 인증 없이 호출해도 200을 반환한다 (내부 API)")
    void users_without_auth_end_to_end() throws Exception {
        // given: 실제 유저 1명 저장
        saveSubscribedUser();
        userRepository.flush();

        // when: Authorization 헤더 없이 유저 목록 API 호출
        var result = mockMvc.perform(get("/internal/v1/users"));

        // then: 인증 없이도 200 반환
        result.andExpect(status().isOk());
    }

    @Test
    @DisplayName("유저 목록 조회: 시뮬레이션 권한이 없는 유저는 목록에서 제외된다")
    void users_excludes_users_without_entitlement_end_to_end() throws Exception {
        // given: 구독 유저 1명과 미구독 유저 1명
        User subscribed = saveSubscribedUser();
        saveUser();
        userRepository.flush();

        // when: 유저 목록 API 호출
        var result = mockMvc.perform(get("/internal/v1/users"));

        // then: 구독 유저만 내려온다 (시뮬레이션 대상이 유료 유저로 한정된다)
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.userIds", contains(subscribed.getId().toString())));
    }

    @Test
    @DisplayName("유저 목록 조회: 구독이 만료된 유저는 목록에서 제외된다")
    void users_excludes_expired_entitlement_end_to_end() throws Exception {
        // given: 만료된 권한만 가진 유저
        User expired = saveUser();
        userEntitlementRepository.save(UserEntitlement.create(
                expired.getId(), EntitlementReader.SIMULATION_ACCESS, Instant.now().minus(Duration.ofDays(1)), Instant.now()));
        userRepository.flush();

        // when: 유저 목록 API 호출
        var result = mockMvc.perform(get("/internal/v1/users"));

        // then: 만료된 유저는 제외된다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.userIds", hasSize(0)));
    }

    @Test
    @DisplayName("유저 목록 조회: 구독 중이어도 평행우주 입장 필수 약관 최신 버전에 동의하지 않았으면 제외된다")
    void users_excludes_users_without_required_consent_end_to_end() throws Exception {
        // given: 최신 버전에 동의한 구독 유저, 동의가 없는 구독 유저, 구버전에만 동의한 구독 유저, 동의를 철회한 구독 유저
        User agreed = saveSubscribedUser();
        saveSubscribedUserWithoutConsent();
        User oldVersionOnly = saveSubscribedUserWithoutConsent();
        agreementRepository.save(Agreement.create(oldVersionOnly.getId(), oldParallelEntryPolicyId(), Instant.now()));
        User revoked = saveSubscribedUser();
        agreementRepository.findAllByUserIdAndRevokedAtIsNull(revoked.getId())
                .forEach(agreement -> ReflectionTestUtils.setField(agreement, "revokedAt", Instant.now()));
        userRepository.flush();
        agreementRepository.flush();

        // when: 유저 목록 API 호출
        var result = mockMvc.perform(get("/internal/v1/users"));

        // then: 최신 필수 약관에 유효한 동의가 있는 유저만 시뮬레이션 대상으로 내려온다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.userIds", contains(agreed.getId().toString())));
    }

    @Test
    @DisplayName("유저 목록 조회: 평행우주 입장 필수 약관이 없으면 구독만으로 목록에 포함된다")
    void users_includes_subscribed_users_when_no_required_policy_end_to_end() throws Exception {
        // given: 필수 약관 버전을 모두 비우고, 동의 기록이 없는 구독 유저 1명
        policyRepository.deleteAllInBatch();
        User subscribed = saveSubscribedUserWithoutConsent();
        userRepository.flush();

        // when: 유저 목록 API 호출
        var result = mockMvc.perform(get("/internal/v1/users"));

        // then: 빈 id 목록으로도 쿼리가 깨지지 않고 구독 유저가 내려온다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.userIds", contains(subscribed.getId().toString())));
    }

    private User saveSubscribedUser() {
        User user = saveSubscribedUserWithoutConsent();
        agreeRequiredParallelEntryPolicies(user.getId());
        return user;
    }

    private User saveSubscribedUserWithoutConsent() {
        User user = saveUser();
        userEntitlementRepository.save(UserEntitlement.create(
                user.getId(), EntitlementReader.SIMULATION_ACCESS, Instant.now().plus(Duration.ofDays(30)), Instant.now()));
        return user;
    }

    private Long oldParallelEntryPolicyId() {
        Long policyNameId = policyNameRepository.findAllByIdentifierIn(List.of("thirdPartyRealIdentityDisclosure")).getFirst().getId();
        return policyRepository.findAllProjectedByPolicyNameIdIn(List.of(policyNameId)).stream()
                .filter(policy -> policy.getVersion().equals("1.0"))
                .findFirst()
                .orElseThrow()
                .getId();
    }
}
