package com.nidus.twinly.support;

import com.nidus.twinly.legal.repository.PolicyRepository.PolicySummary;

import java.time.Instant;

/** 정책 조회는 엔티티 전체가 아니라 프로젝션을 쓴다. 테스트에서 그 프로젝션을 만들기 위한 픽스처. */
public record TestPolicySummary(Long id, Long policyNameId, String version, String key,
                                Boolean isRequired, Instant effectiveAt) implements PolicySummary {

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public Long getPolicyNameId() {
        return policyNameId;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public Boolean getIsRequired() {
        return isRequired;
    }

    @Override
    public Instant getEffectiveAt() {
        return effectiveAt;
    }
}
