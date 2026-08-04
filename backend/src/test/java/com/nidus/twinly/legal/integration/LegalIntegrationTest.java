package com.nidus.twinly.legal.integration;

import com.nidus.twinly.common.aws.cloudfront.CloudFrontProperties;
import com.nidus.twinly.legal.entity.Policy;
import com.nidus.twinly.legal.entity.PolicyName;
import com.nidus.twinly.legal.repository.PolicyNameRepository;
import com.nidus.twinly.legal.repository.PolicyRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LegalIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    PolicyNameRepository policyNameRepository;

    @Autowired
    PolicyRepository policyRepository;

    @Autowired
    CloudFrontProperties cloudFrontProperties;

    @PersistenceContext
    EntityManager entityManager;

    /**
     * V2 마이그레이션이 넣는 운영 정책명 6건을 비운다.
     * 아래 테스트들은 응답 배열 전체를 단언하므로, 픽스처 외의 행이 있으면 개수와 순서가 어긋난다.
     * 베이스 클래스가 @Transactional 이라 이 삭제는 테스트가 끝나면 롤백된다.
     */
    @BeforeEach
    void clearSeededPolicyNames() {
        policyRepository.deleteAllInBatch();
        policyNameRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("정책 목록 조회: 폐기된 정책명은 빠지고, 시행일이 지난 최신 버전이 실제 DB에서 조회되어 응답된다")
    void policies_success_end_to_end() throws Exception {
        // given: 활성 정책명 1건(과거 시행 v1·v2, 미래 시행 v3)과 폐기된 정책명 1건(과거 시행 v1)을 실제 DB에 저장
        PolicyName terms = savePolicyName("terms_of_service", "서비스 이용약관", false);
        savePolicy(terms.getId(), "1", "legal/terms/v1.html", false, Instant.parse("2020-01-01T00:00:00Z"));
        savePolicy(terms.getId(), "2", "legal/terms/v2.html", true, Instant.parse("2021-01-01T00:00:00Z"));
        savePolicy(terms.getId(), "3", "legal/terms/v3.html", true, Instant.parse("2999-01-01T00:00:00Z"));

        PolicyName deprecated = savePolicyName("legacy_policy", "구 이용약관", true);
        savePolicy(deprecated.getId(), "1", "legal/legacy/v1.html", true, Instant.parse("2020-01-01T00:00:00Z"));

        Long termsId = terms.getId();
        Long deprecatedId = deprecated.getId();
        flushAndClear();

        // when: 인증 헤더 없이 정책 목록 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/legal/policies"));

        // then: 200 + 폐기 정책명은 제외되고 시행된 최신 버전(v2)만 문자열 version으로 응답된다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.policies.length()").value(1))
                .andExpect(jsonPath("$.policies[0].policyId").value("terms_of_service"))
                .andExpect(jsonPath("$.policies[0].title").value("서비스 이용약관"))
                .andExpect(jsonPath("$.policies[0].version").value("2"))
                .andExpect(jsonPath("$.policies[0].url").value("https://" + cloudFrontProperties.domain() + "/legal/terms/v2.html"))
                .andExpect(jsonPath("$.policies[0].isRequired").value(true));

        // then: 조회 API이므로 DB의 정책 버전 4건은 그대로 남아 있다
        assertThat(policyRepository.findAllProjectedByPolicyNameIdIn(List.of(termsId, deprecatedId))).hasSize(4);
    }

    @Test
    @DisplayName("정책 목록 조회: 아직 시행되지 않은 버전만 있는 정책명은 version/url/isRequired 없이 이름만 응답된다")
    void policies_without_effective_version_end_to_end() throws Exception {
        // given: 활성 정책명 1건과 미래 시행 버전 1건만 실제 DB에 저장
        PolicyName privacy = savePolicyName("privacy_policy", "개인정보 처리방침", false);
        savePolicy(privacy.getId(), "1", "legal/privacy/v1.html", true, Instant.parse("2999-01-01T00:00:00Z"));
        flushAndClear();

        // when: 정책 목록 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/legal/policies"));

        // then: 200 + 정책명만 내려가고 버전 관련 필드는 비어 있다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.policies.length()").value(1))
                .andExpect(jsonPath("$.policies[0].policyId").value("privacy_policy"))
                .andExpect(jsonPath("$.policies[0].title").value("개인정보 처리방침"))
                .andExpect(jsonPath("$.policies[0].version").doesNotExist())
                .andExpect(jsonPath("$.policies[0].url").doesNotExist())
                .andExpect(jsonPath("$.policies[0].isRequired").doesNotExist());
    }

    @Test
    @DisplayName("정책 목록 조회: 이름 역순으로 저장해도 응답 순서는 id 오름차순으로 고정된다")
    void policies_are_ordered_by_id_end_to_end() throws Exception {
        // given: 이름 오름차순과 어긋나게 저장해 DB 반환 순서에 기대지 않았음을 드러낸다
        PolicyName third = savePolicyName("zzz_policy", "다 약관", false);
        savePolicy(third.getId(), "1", "legal/zzz/v1.html", true, Instant.parse("2020-01-01T00:00:00Z"));
        PolicyName second = savePolicyName("mmm_policy", "나 약관", false);
        savePolicy(second.getId(), "1", "legal/mmm/v1.html", true, Instant.parse("2020-01-01T00:00:00Z"));
        PolicyName first = savePolicyName("aaa_policy", "가 약관", false);
        savePolicy(first.getId(), "1", "legal/aaa/v1.html", true, Instant.parse("2020-01-01T00:00:00Z"));
        flushAndClear();

        // when: 정책 목록 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/legal/policies"));

        // then: ORDER BY가 없으면 DB 반환 순서에 좌우되는 자리다. 저장 순서(=id 순)로 고정된다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.policies.length()").value(3))
                .andExpect(jsonPath("$.policies[0].policyId").value("zzz_policy"))
                .andExpect(jsonPath("$.policies[1].policyId").value("mmm_policy"))
                .andExpect(jsonPath("$.policies[2].policyId").value("aaa_policy"));
    }

    /** 영속성 컨텍스트를 비워 이후 조회가 실제 DB를 타도록 강제한다. */
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private PolicyName savePolicyName(String identifier, String name, boolean deprecated) {
        PolicyName policyName = BeanUtils.instantiateClass(PolicyName.class);
        ReflectionTestUtils.setField(policyName, "identifier", identifier);
        ReflectionTestUtils.setField(policyName, "name", name);
        ReflectionTestUtils.setField(policyName, "requiresAgreement", true);
        ReflectionTestUtils.setField(policyName, "isDeprecated", deprecated);
        return policyNameRepository.save(policyName);
    }

    private Policy savePolicy(Long policyNameId, String version, String key, Boolean isRequired, Instant effectiveAt) {
        Policy policy = BeanUtils.instantiateClass(Policy.class);
        ReflectionTestUtils.setField(policy, "policyNameId", policyNameId);
        ReflectionTestUtils.setField(policy, "version", version);
        ReflectionTestUtils.setField(policy, "key", key);
        ReflectionTestUtils.setField(policy, "isRequired", isRequired);
        ReflectionTestUtils.setField(policy, "effectiveAt", effectiveAt);
        ReflectionTestUtils.setField(policy, "createdAt", Instant.now());
        return policyRepository.save(policy);
    }
}
