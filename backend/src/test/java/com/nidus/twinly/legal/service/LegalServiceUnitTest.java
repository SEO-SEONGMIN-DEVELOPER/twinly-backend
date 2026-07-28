package com.nidus.twinly.legal.service;

import com.nidus.twinly.legal.dto.result.LegalPoliciesItemResult;
import com.nidus.twinly.legal.dto.result.LegalPoliciesResult;
import com.nidus.twinly.legal.entity.Policy;
import com.nidus.twinly.legal.entity.PolicyName;
import com.nidus.twinly.legal.repository.PolicyNameRepository;
import com.nidus.twinly.legal.repository.PolicyRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class LegalServiceUnitTest {

    @Mock
    PolicyNameRepository policyNameRepository;

    @Mock
    PolicyRepository policyRepository;

    @InjectMocks
    LegalService legalService;

    @Test
    @DisplayName("폐기되지 않은 정책명 id 목록으로 버전을 한 번에 조회하고, 정책명마다 가장 최근에 시행된 버전으로 매핑한다")
    void policies_picks_latest_effective_version_per_policy_name() {
        // given: 폐기되지 않은 정책명 2건과, 이용약관은 시행일이 다른 버전 2개 / 개인정보 처리방침은 버전 1개
        PolicyName terms = policyName(1L, "terms_of_service", "서비스 이용약관");
        PolicyName privacy = policyName(2L, "privacy_policy", "개인정보 처리방침");
        given(policyNameRepository.findAllByIsDeprecatedFalse()).willReturn(List.of(terms, privacy));
        given(policyRepository.findAllByPolicyNameIdIn(List.of(1L, 2L))).willReturn(List.of(
                policy(1L, 1, "https://cdn.twinly.app/terms/v1.html", false, Instant.parse("2024-01-01T00:00:00Z")),
                policy(1L, 2, "https://cdn.twinly.app/terms/v2.html", true, Instant.parse("2025-01-01T00:00:00Z")),
                policy(2L, 3, "https://cdn.twinly.app/privacy/v3.html", false, Instant.parse("2024-06-01T00:00:00Z"))));

        // when: 정책 목록 조회
        LegalPoliciesResult result = legalService.policies();

        // then: 정책명 순서대로 각각의 최신 시행 버전(이용약관 v2, 개인정보 v3)이 매핑된다
        assertThat(result.policies())
                .extracting(
                        LegalPoliciesItemResult::policyId,
                        LegalPoliciesItemResult::title,
                        LegalPoliciesItemResult::version,
                        LegalPoliciesItemResult::url,
                        LegalPoliciesItemResult::isRequired)
                .containsExactly(
                        tuple("terms_of_service", "서비스 이용약관", 2, "https://cdn.twinly.app/terms/v2.html", true),
                        tuple("privacy_policy", "개인정보 처리방침", 3, "https://cdn.twinly.app/privacy/v3.html", false));

        // then: 버전 조회는 폐기되지 않은 정책명 id 목록으로 단 한 번만 위임된다
        then(policyRepository).should().findAllByPolicyNameIdIn(List.of(1L, 2L));
    }

    @Test
    @DisplayName("시행일이 아직 오지 않았거나 비어 있는 버전만 있으면 version/url/isRequired가 null인 항목을 만든다")
    void policies_excludes_versions_not_yet_effective() {
        // given: 폐기되지 않은 정책명 1건에 시행일 미설정 버전과 미래 시행 버전만 존재
        PolicyName privacy = policyName(2L, "privacy_policy", "개인정보 처리방침");
        given(policyNameRepository.findAllByIsDeprecatedFalse()).willReturn(List.of(privacy));
        given(policyRepository.findAllByPolicyNameIdIn(List.of(2L))).willReturn(List.of(
                policy(2L, 1, "https://cdn.twinly.app/privacy/v1.html", true, null),
                policy(2L, 2, "https://cdn.twinly.app/privacy/v2.html", true, Instant.now().plus(1, ChronoUnit.DAYS))));

        // when: 정책 목록 조회
        LegalPoliciesResult result = legalService.policies();

        // then: 정책명은 그대로 노출되지만 버전 관련 값은 모두 null
        assertThat(result.policies()).hasSize(1);
        LegalPoliciesItemResult item = result.policies().getFirst();
        assertThat(item.policyId()).isEqualTo("privacy_policy");
        assertThat(item.title()).isEqualTo("개인정보 처리방침");
        assertThat(item.version()).isNull();
        assertThat(item.url()).isNull();
        assertThat(item.isRequired()).isNull();
    }

    @Test
    @DisplayName("노출할 정책명이 하나도 없으면 빈 목록을 반환하고 빈 id 목록으로 버전을 조회한다")
    void policies_returns_empty_when_no_policy_names() {
        // given: 폐기되지 않은 정책명이 하나도 없음
        given(policyNameRepository.findAllByIsDeprecatedFalse()).willReturn(List.of());

        // when: 정책 목록 조회
        LegalPoliciesResult result = legalService.policies();

        // then: 빈 목록을 반환하고, 버전 조회에는 빈 id 목록이 전달된다
        assertThat(result.policies()).isEmpty();
        then(policyRepository).should().findAllByPolicyNameIdIn(List.of());
    }

    private PolicyName policyName(Long id, String identifier, String name) {
        PolicyName policyName = BeanUtils.instantiateClass(PolicyName.class);
        ReflectionTestUtils.setField(policyName, "id", id);
        ReflectionTestUtils.setField(policyName, "identifier", identifier);
        ReflectionTestUtils.setField(policyName, "name", name);
        ReflectionTestUtils.setField(policyName, "isDeprecated", false);
        return policyName;
    }

    private Policy policy(Long policyNameId, Integer version, String url, Boolean isRequired, Instant effectiveAt) {
        Policy policy = BeanUtils.instantiateClass(Policy.class);
        ReflectionTestUtils.setField(policy, "policyNameId", policyNameId);
        ReflectionTestUtils.setField(policy, "version", version);
        ReflectionTestUtils.setField(policy, "content", "content-v" + version);
        ReflectionTestUtils.setField(policy, "url", url);
        ReflectionTestUtils.setField(policy, "isRequired", isRequired);
        ReflectionTestUtils.setField(policy, "effectiveAt", effectiveAt);
        ReflectionTestUtils.setField(policy, "createdAt", Instant.now());
        return policy;
    }
}
