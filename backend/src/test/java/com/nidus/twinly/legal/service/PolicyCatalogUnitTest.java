package com.nidus.twinly.legal.service;

import com.nidus.twinly.legal.config.LegalProperties;
import com.nidus.twinly.legal.domain.PolicyKind;
import com.nidus.twinly.legal.domain.RequiredPolicies;
import com.nidus.twinly.legal.entity.PolicyName;
import com.nidus.twinly.legal.repository.PolicyRepository.PolicySummary;
import com.nidus.twinly.support.TestPolicySummary;
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
import java.util.Set;

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
        given(policyRepository.findAllProjectedByPolicyNameIdIn(anyList())).willReturn(List.of(
                policy(101L, 1L, "1", Instant.parse("2024-01-01T00:00:00Z")),
                policy(102L, 1L, "2", Instant.parse("2025-01-01T00:00:00Z"))));

        // then: 구버전 클라이언트가 v1을 보내도 처리할 수 있도록 두 버전 모두 남는다
        assertThat(policyCatalog.loadByKey(List.of("terms_of_service")))
                .containsOnlyKeys(new PolicyKey("terms_of_service", "1"), new PolicyKey("terms_of_service", "2"));
    }

    @Test
    @DisplayName("아직 시행일이 오지 않은 버전은 동의 대상에서 제외한다")
    void loadByKey_excludes_future_version() {
        // given: 시행 중인 v1과 아직 시행 전인 v2가 존재
        given(policyNameRepository.findAllByIdentifierIn(List.of("terms_of_service")))
                .willReturn(List.of(policyName(1L, "terms_of_service")));
        given(policyRepository.findAllProjectedByPolicyNameIdIn(anyList())).willReturn(List.of(
                policy(101L, 1L, "1", Instant.now().minus(1, ChronoUnit.DAYS)),
                policy(102L, 1L, "2", Instant.now().plus(1, ChronoUnit.DAYS))));

        // when: 동의 대상 정책 조회
        Map<PolicyKey, PolicySummary> result = policyCatalog.loadByKey(List.of("terms_of_service"));

        // then: 미래 버전 v2는 빠지고 이미 시행된 v1만 남는다
        assertThat(result).containsOnlyKeys(new PolicyKey("terms_of_service", "1"));
    }

    @Test
    @DisplayName("시행일이 비어 있는 버전만 있으면 동의 대상이 하나도 없다")
    void loadByKey_excludes_version_without_effective_at() {
        // given: 시행일이 설정되지 않은 버전만 존재
        given(policyNameRepository.findAllByIdentifierIn(List.of("terms_of_service")))
                .willReturn(List.of(policyName(1L, "terms_of_service")));
        given(policyRepository.findAllProjectedByPolicyNameIdIn(anyList()))
                .willReturn(List.of(policy(101L, 1L, "1", null)));

        // when: 동의 대상 정책 조회
        Map<PolicyKey, PolicySummary> result = policyCatalog.loadByKey(List.of("terms_of_service"));

        // then: 동의할 수 있는 버전이 없다
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("정책명별 노출 버전은 시행 중인 것 중 가장 최근 시행 버전 하나로 좁힌다")
    void loadLatestByPolicyNameId_picks_latest_effective_version() {
        // given: 이용약관에 시행 중인 v1(2024)·v2(2025)와 아직 시행 전인 v3가 존재
        given(policyRepository.findAllProjectedByPolicyNameIdIn(List.of(1L))).willReturn(List.of(
                policy(101L, 1L, "1", Instant.parse("2024-01-01T00:00:00Z")),
                policy(102L, 1L, "2", Instant.parse("2025-01-01T00:00:00Z")),
                policy(103L, 1L, "3", Instant.now().plus(1, ChronoUnit.DAYS))));

        // when: 정책명별 노출 버전 조회
        Map<Long, PolicySummary> result = policyCatalog.loadLatestByPolicyNameId(List.of(1L));

        // then: 미래 버전 v3는 제외되고 시행 중인 최신 v2가 선택된다
        assertThat(result).containsOnlyKeys(1L);
        assertThat(result.get(1L).getVersion()).isEqualTo("2");
    }

    @Test
    @DisplayName("정책명별 노출 버전도 시행일이 같으면 나중에 등록된 판으로 결정한다")
    void loadLatestByPolicyNameId_breaks_effective_at_tie_by_id() {
        // given: 시행일이 동일한 v2와 v3가 존재하고 리포지토리가 나중에 등록된 v3를 먼저 돌려줌
        Instant sameEffectiveAt = Instant.parse("2025-01-01T00:00:00Z");
        given(policyRepository.findAllProjectedByPolicyNameIdIn(List.of(1L))).willReturn(List.of(
                policy(103L, 1L, "3", sameEffectiveAt),
                policy(102L, 1L, "2", sameEffectiveAt)));

        // when: 정책명별 노출 버전 조회
        Map<Long, PolicySummary> result = policyCatalog.loadLatestByPolicyNameId(List.of(1L));

        // then: 조회 순서와 무관하게 항상 v3로 결정된다 (약관 조회와 동의 여부 판정이 같은 버전을 본다)
        assertThat(result.get(1L).getVersion()).isEqualTo("3");
    }

    @Test
    @DisplayName("시행 중인 버전이 없는 정책명은 노출 버전 맵에서 빠진다")
    void loadLatestByPolicyNameId_excludes_policy_name_without_effective_version() {
        // given: 시행일이 설정되지 않은 버전만 존재
        given(policyRepository.findAllProjectedByPolicyNameIdIn(List.of(1L)))
                .willReturn(List.of(policy(101L, 1L, "1", null)));

        // when: 정책명별 노출 버전 조회
        Map<Long, PolicySummary> result = policyCatalog.loadLatestByPolicyNameId(List.of(1L));

        // then: 해당 정책명 자체가 맵에 없다
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("필수 동의 대상은 동의를 받는 정책명 중 is_required 인 최신 버전만이고, 고지용 정책은 필수여도 빠진다")
    void loadRequiredPolicyIds_excludes_notice_only_policy() {
        // given: 동의를 받는 필수 약관(1)과 고지만 하는 필수 정책(2)이 모두 시행 중
        given(policyNameRepository.findAllByKindAndIsDeprecatedFalseOrderByIdAsc(PolicyKind.ONBOARDING)).willReturn(List.of(
                policyName(1L, "serviceTerms", true),
                policyName(2L, "privacyPolicy", false)));
        given(policyRepository.findAllProjectedByPolicyNameIdIn(List.of(1L))).willReturn(List.of(
                policy(101L, 1L, "1", Instant.parse("2024-01-01T00:00:00Z"))));

        // when: 필수 동의 대상 조회
        Set<Long> result = policyCatalog.loadRequiredPolicyIds(PolicyKind.ONBOARDING);

        // then: 고지용 정책은 동의 기록이 생길 수 없으므로 검사 대상에서 빠지고 동의 약관만 남는다
        assertThat(result).containsExactly(101L);
    }

    @Test
    @DisplayName("최소 버전이 설정된 필수 약관은 최소 버전부터 최신 버전까지를 모두 인정한다")
    void loadRequiredPolicies_accepts_from_minimum_version() {
        // given: 실신원 약관 1.1·1.2·1.3 이 시행 중이고 최소 버전이 1.2
        PolicyCatalog catalog = catalogWithMinimumVersions(Map.of("thirdPartyRealIdentityDisclosure", "1.2"));
        givenRealIdentityVersions();

        // when: 평행우주 입장 필수 약관 조회
        RequiredPolicies result = catalog.loadRequiredPolicies(PolicyKind.PARALLEL_ENTRY);

        // then: 1.1 은 빠지고 1.2·1.3 이 한 묶음으로 인정된다
        assertThat(result.acceptedPolicyIdGroups()).containsExactly(Set.of(202L, 203L));
        assertThat(result.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("최소 버전이 없거나 설정한 버전이 없으면 최신 버전만 인정한다")
    void loadRequiredPolicies_without_minimum_accepts_latest_only() {
        // given: 최소 버전으로 존재하지 않는 9.9 가 설정됨
        PolicyCatalog catalog = catalogWithMinimumVersions(Map.of("thirdPartyRealIdentityDisclosure", "9.9"));
        givenRealIdentityVersions();

        // when: 평행우주 입장 필수 약관 조회
        RequiredPolicies result = catalog.loadRequiredPolicies(PolicyKind.PARALLEL_ENTRY);

        // then: 잘못된 설정으로 입장 조건이 느슨해지지 않고 최신 1.3 만 인정된다
        assertThat(result.acceptedPolicyIdGroups()).containsExactly(Set.of(203L));
    }

    @Test
    @DisplayName("최신 버전이 필수가 아니면 그 약관은 필수 묶음에서 빠진다")
    void loadRequiredPolicies_excludes_name_whose_latest_is_optional() {
        // given: 최신 버전이 선택 동의로 바뀐 약관
        PolicyCatalog catalog = catalogWithMinimumVersions(Map.of());
        given(policyNameRepository.findAllByKindAndIsDeprecatedFalseOrderByIdAsc(PolicyKind.PARALLEL_ENTRY))
                .willReturn(List.of(policyName(1L, "thirdPartyRealIdentityDisclosure", true)));
        given(policyRepository.findAllProjectedByPolicyNameIdIn(List.of(1L))).willReturn(List.of(
                policy(201L, 1L, "1.1", Instant.parse("2026-09-16T15:00:00Z")),
                new TestPolicySummary(202L, 1L, "1.2", false, Instant.parse("2026-10-22T15:00:00Z"))));

        // when & then: 필수 약관이 없으므로 누구나 만족한다
        RequiredPolicies result = catalog.loadRequiredPolicies(PolicyKind.PARALLEL_ENTRY);
        assertThat(result.count()).isZero();
        assertThat(result.isSatisfiedBy(List.of())).isTrue();
    }

    private void givenRealIdentityVersions() {
        given(policyNameRepository.findAllByKindAndIsDeprecatedFalseOrderByIdAsc(PolicyKind.PARALLEL_ENTRY)).willReturn(List.of(
                policyName(1L, "thirdPartyRealIdentityDisclosure", true),
                policyName(2L, "paidServiceRefundPolicy", false)));
        given(policyRepository.findAllProjectedByPolicyNameIdIn(List.of(1L))).willReturn(List.of(
                policy(201L, 1L, "1.1", Instant.parse("2026-09-16T15:00:00Z")),
                policy(202L, 1L, "1.2", Instant.parse("2026-10-22T15:00:00Z")),
                policy(203L, 1L, "1.3", Instant.parse("2026-11-01T15:00:00Z"))));
    }

    private PolicyCatalog catalogWithMinimumVersions(Map<String, String> minimumVersions) {
        return new PolicyCatalog(policyRepository, policyNameRepository, new LegalProperties("https://trytwinly.com", minimumVersions));
    }

    private PolicyName policyName(Long id, String identifier, boolean requiresAgreement) {
        PolicyName policyName = policyName(id, identifier);
        ReflectionTestUtils.setField(policyName, "requiresAgreement", requiresAgreement);
        return policyName;
    }

    private PolicyName policyName(Long id, String identifier) {
        PolicyName policyName = BeanUtils.instantiateClass(PolicyName.class);
        ReflectionTestUtils.setField(policyName, "id", id);
        ReflectionTestUtils.setField(policyName, "identifier", identifier);
        ReflectionTestUtils.setField(policyName, "name", "이름");
        ReflectionTestUtils.setField(policyName, "isDeprecated", false);
        return policyName;
    }

    private TestPolicySummary policy(Long id, Long policyNameId, String version, Instant effectiveAt) {
        return new TestPolicySummary(id, policyNameId, version, true, effectiveAt);
    }
}
