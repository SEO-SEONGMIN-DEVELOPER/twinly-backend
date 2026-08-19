package com.nidus.twinly.legal.service;

import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.legal.dto.result.LegalPoliciesItemResult;
import com.nidus.twinly.legal.dto.result.LegalPoliciesResult;
import com.nidus.twinly.legal.domain.PolicyKind;
import com.nidus.twinly.legal.entity.PolicyName;
import com.nidus.twinly.support.TestPolicySummary;
import com.nidus.twinly.legal.repository.PolicyNameRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class LegalServiceUnitTest {

    @Mock
    PolicyNameRepository policyNameRepository;

    @Mock
    PolicyCatalog policyCatalog;

    @Mock
    CloudFrontService cloudFrontService;

    @InjectMocks
    LegalService legalService;

    @Test
    @DisplayName("폐기되지 않은 정책명 id 목록을 카탈로그에 한 번 넘기고, 돌려받은 버전을 정책명 순서대로 매핑한다")
    void policies_maps_catalog_result_per_policy_name() {
        // given: 폐기되지 않은 정책명 2건에 대해 카탈로그가 시행 중인 최신 버전을 돌려줌
        PolicyName terms = policyName(1L, "terms_of_service", "서비스 이용약관");
        PolicyName privacy = policyName(2L, "privacy_policy", "개인정보 처리방침");
        given(policyNameRepository.findAllByKindAndIsDeprecatedFalseOrderByIdAsc(PolicyKind.ONBOARDING)).willReturn(List.of(terms, privacy));
        given(policyCatalog.loadLatestByPolicyNameId(List.of(1L, 2L))).willReturn(Map.of(
                1L, policy(102L, 1L, "2", "legal/terms/v2.html", true),
                2L, policy(203L, 2L, "3", "legal/privacy/v3.html", false)));
        given(cloudFrontService.getPublicUrl("legal/terms/v2.html")).willReturn("https://cdn.twinly.app/legal/terms/v2.html");
        given(cloudFrontService.getPublicUrl("legal/privacy/v3.html")).willReturn("https://cdn.twinly.app/legal/privacy/v3.html");

        // when: 정책 목록 조회
        LegalPoliciesResult result = legalService.policies(PolicyKind.ONBOARDING);

        // then: 정책명 순서대로 카탈로그가 고른 버전이 실린다
        assertThat(result.policies())
                .extracting(
                        LegalPoliciesItemResult::policyId,
                        LegalPoliciesItemResult::title,
                        LegalPoliciesItemResult::version,
                        LegalPoliciesItemResult::url,
                        LegalPoliciesItemResult::isRequired)
                .containsExactly(
                        tuple("terms_of_service", "서비스 이용약관", "2", "https://cdn.twinly.app/legal/terms/v2.html", true),
                        tuple("privacy_policy", "개인정보 처리방침", "3", "https://cdn.twinly.app/legal/privacy/v3.html", false));

        // then: 버전 조회는 정책명 id 목록으로 단 한 번만 위임된다
        then(policyCatalog).should().loadLatestByPolicyNameId(List.of(1L, 2L));
    }

    @Test
    @DisplayName("카탈로그가 돌려주지 않은 정책명은 항목은 남기되 version/url/isRequired가 null이 된다")
    void policies_keeps_policy_name_without_effective_version() {
        // given: 시행 중인 버전이 없어 카탈로그가 비어 있는 상태
        PolicyName privacy = policyName(2L, "privacy_policy", "개인정보 처리방침");
        given(policyNameRepository.findAllByKindAndIsDeprecatedFalseOrderByIdAsc(PolicyKind.ONBOARDING)).willReturn(List.of(privacy));
        given(policyCatalog.loadLatestByPolicyNameId(List.of(2L))).willReturn(Map.of());

        // when: 정책 목록 조회
        LegalPoliciesResult result = legalService.policies(PolicyKind.ONBOARDING);

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
        given(policyNameRepository.findAllByKindAndIsDeprecatedFalseOrderByIdAsc(PolicyKind.ONBOARDING)).willReturn(List.of());
        given(policyCatalog.loadLatestByPolicyNameId(List.of())).willReturn(Map.of());

        // when: 정책 목록 조회
        LegalPoliciesResult result = legalService.policies(PolicyKind.ONBOARDING);

        // then: 빈 목록을 반환하고, 버전 조회에는 빈 id 목록이 전달된다
        assertThat(result.policies()).isEmpty();
        then(policyCatalog).should().loadLatestByPolicyNameId(List.of());
    }

    @Test
    @DisplayName("kind로 조회 대상이 갈리고, isRequired는 정책에 저장된 값이 그대로 나간다")
    void policies_filters_by_kind_and_keeps_stored_is_required() {
        // given: is_required가 false로 저장된 평행우주 입장 정책
        PolicyName provision = policyName(9L, "parallelRelationProvision", "평행우주 관계 제3자 제공 동의");
        given(policyNameRepository.findAllByKindAndIsDeprecatedFalseOrderByIdAsc(PolicyKind.PARALLEL_ENTRY)).willReturn(List.of(provision));
        given(policyCatalog.loadLatestByPolicyNameId(List.of(9L))).willReturn(Map.of(
                9L, policy(901L, 9L, "1", "legal/parallel/v1.html", false)));
        given(cloudFrontService.getPublicUrl("legal/parallel/v1.html")).willReturn("https://cdn.twinly.app/legal/parallel/v1.html");

        // when: 평행우주 입장 목록 조회
        LegalPoliciesResult result = legalService.policies(PolicyKind.PARALLEL_ENTRY);

        // then: 저장된 값이 가공 없이 그대로 실린다
        assertThat(result.policies()).hasSize(1);
        assertThat(result.policies().getFirst().policyId()).isEqualTo("parallelRelationProvision");
        assertThat(result.policies().getFirst().isRequired()).isFalse();
    }

    private PolicyName policyName(Long id, String identifier, String name) {
        PolicyName policyName = BeanUtils.instantiateClass(PolicyName.class);
        ReflectionTestUtils.setField(policyName, "id", id);
        ReflectionTestUtils.setField(policyName, "identifier", identifier);
        ReflectionTestUtils.setField(policyName, "name", name);
        ReflectionTestUtils.setField(policyName, "isDeprecated", false);
        return policyName;
    }

    private TestPolicySummary policy(Long id, Long policyNameId, String version, String key, Boolean isRequired) {
        return new TestPolicySummary(id, policyNameId, version, key, isRequired,
                Instant.parse("2025-01-01T00:00:00Z"));
    }
}
