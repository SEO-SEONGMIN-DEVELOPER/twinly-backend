package com.nidus.twinly.legal.service;

import com.nidus.twinly.legal.entity.Policy;
import com.nidus.twinly.legal.entity.PolicyName;
import com.nidus.twinly.legal.repository.PolicyNameRepository;
import com.nidus.twinly.legal.repository.PolicyRepository;
import com.nidus.twinly.legal.service.PolicyCatalog.PolicyKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PolicyCatalogUnitTest {

    @Mock
    PolicyRepository policyRepository;

    @Mock
    PolicyNameRepository policyNameRepository;

    @InjectMocks
    PolicyCatalog policyCatalog;

    @Test
    @DisplayName("이미 시행된 버전은 구버전도 모두 동의·철회 대상으로 반환한다")
    void loadByKey_returns_all_effective_versions() {
        // given: 이용약관에 시행 중인 v1(2024)과 v2(2025)가 모두 존재
        given(policyNameRepository.findAllByIdentifierIn(List.of("terms_of_service")))
                .willReturn(List.of(policyName(1L, "terms_of_service")));
        given(policyRepository.findAllByPolicyNameIdIn(anyList())).willReturn(List.of(
                policy(1L, 1, Instant.parse("2024-01-01T00:00:00Z")),
                policy(1L, 2, Instant.parse("2025-01-01T00:00:00Z"))));

        // then: 구버전 클라이언트가 v1을 보내도 처리할 수 있도록 두 버전 모두 남는다
        assertThat(policyCatalog.loadByKey(List.of("terms_of_service")))
                .containsOnlyKeys(new PolicyKey("terms_of_service", 1), new PolicyKey("terms_of_service", 2));
    }

    @Test
    @DisplayName("아직 시행일이 오지 않은 버전은 동의 대상에서 제외한다")
    void loadByKey_excludes_future_version() {
        // given: 시행 중인 v1과 아직 시행 전인 v2가 존재
        given(policyNameRepository.findAllByIdentifierIn(List.of("terms_of_service")))
                .willReturn(List.of(policyName(1L, "terms_of_service")));
        given(policyRepository.findAllByPolicyNameIdIn(anyList())).willReturn(List.of(
                policy(1L, 1, Instant.now().minus(1, ChronoUnit.DAYS)),
                policy(1L, 2, Instant.now().plus(1, ChronoUnit.DAYS))));

        // when: 동의 대상 정책 조회
        Map<PolicyKey, Policy> result = policyCatalog.loadByKey(List.of("terms_of_service"));

        // then: 미래 버전 v2는 빠지고 이미 시행된 v1만 남는다
        assertThat(result).containsOnlyKeys(new PolicyKey("terms_of_service", 1));
    }

    @Test
    @DisplayName("시행일이 비어 있는 버전만 있으면 동의 대상이 하나도 없다")
    void loadByKey_excludes_version_without_effective_at() {
        // given: 시행일이 설정되지 않은 버전만 존재
        given(policyNameRepository.findAllByIdentifierIn(List.of("terms_of_service")))
                .willReturn(List.of(policyName(1L, "terms_of_service")));
        given(policyRepository.findAllByPolicyNameIdIn(anyList()))
                .willReturn(List.of(policy(1L, 1, null)));

        // when: 동의 대상 정책 조회
        Map<PolicyKey, Policy> result = policyCatalog.loadByKey(List.of("terms_of_service"));

        // then: 동의할 수 있는 버전이 없다
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("정책명별 노출 버전은 시행 중인 것 중 가장 최근 시행 버전 하나로 좁힌다")
    void loadLatestByPolicyNameId_picks_latest_effective_version() {
        // given: 이용약관에 시행 중인 v1(2024)·v2(2025)와 아직 시행 전인 v3가 존재
        given(policyRepository.findAllByPolicyNameIdIn(List.of(1L))).willReturn(List.of(
                policy(1L, 1, Instant.parse("2024-01-01T00:00:00Z")),
                policy(1L, 2, Instant.parse("2025-01-01T00:00:00Z")),
                policy(1L, 3, Instant.now().plus(1, ChronoUnit.DAYS))));

        // when: 정책명별 노출 버전 조회
        Map<Long, Policy> result = policyCatalog.loadLatestByPolicyNameId(List.of(1L));

        // then: 미래 버전 v3는 제외되고 시행 중인 최신 v2가 선택된다
        assertThat(result).containsOnlyKeys(1L);
        assertThat(result.get(1L).getVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("정책명별 노출 버전도 시행일이 같으면 더 큰 버전 번호로 결정한다")
    void loadLatestByPolicyNameId_breaks_effective_at_tie_by_version() {
        // given: 시행일이 동일한 v2와 v3가 존재하고 리포지토리가 v3를 먼저 돌려줌
        Instant sameEffectiveAt = Instant.parse("2025-01-01T00:00:00Z");
        given(policyRepository.findAllByPolicyNameIdIn(List.of(1L))).willReturn(List.of(
                policy(1L, 3, sameEffectiveAt),
                policy(1L, 2, sameEffectiveAt)));

        // when: 정책명별 노출 버전 조회
        Map<Long, Policy> result = policyCatalog.loadLatestByPolicyNameId(List.of(1L));

        // then: 조회 순서와 무관하게 항상 v3로 결정된다 (약관 조회와 동의 여부 판정이 같은 버전을 본다)
        assertThat(result.get(1L).getVersion()).isEqualTo(3);
    }

    @Test
    @DisplayName("시행 중인 버전이 없는 정책명은 노출 버전 맵에서 빠진다")
    void loadLatestByPolicyNameId_excludes_policy_name_without_effective_version() {
        // given: 시행일이 설정되지 않은 버전만 존재
        given(policyRepository.findAllByPolicyNameIdIn(List.of(1L)))
                .willReturn(List.of(policy(1L, 1, null)));

        // when: 정책명별 노출 버전 조회
        Map<Long, Policy> result = policyCatalog.loadLatestByPolicyNameId(List.of(1L));

        // then: 해당 정책명 자체가 맵에 없다
        assertThat(result).isEmpty();
    }

    private PolicyName policyName(Long id, String identifier) {
        PolicyName policyName = BeanUtils.instantiateClass(PolicyName.class);
        ReflectionTestUtils.setField(policyName, "id", id);
        ReflectionTestUtils.setField(policyName, "identifier", identifier);
        ReflectionTestUtils.setField(policyName, "name", "이름");
        ReflectionTestUtils.setField(policyName, "isDeprecated", false);
        return policyName;
    }

    private Policy policy(Long policyNameId, Integer version, Instant effectiveAt) {
        Policy policy = BeanUtils.instantiateClass(Policy.class);
        ReflectionTestUtils.setField(policy, "id", policyNameId * 100 + version);
        ReflectionTestUtils.setField(policy, "policyNameId", policyNameId);
        ReflectionTestUtils.setField(policy, "version", version);
        ReflectionTestUtils.setField(policy, "content", "content-v" + version);
        ReflectionTestUtils.setField(policy, "url", "https://cdn.twinly.app/v" + version + ".html");
        ReflectionTestUtils.setField(policy, "isRequired", true);
        ReflectionTestUtils.setField(policy, "effectiveAt", effectiveAt);
        ReflectionTestUtils.setField(policy, "createdAt", Instant.now());
        return policy;
    }
}
