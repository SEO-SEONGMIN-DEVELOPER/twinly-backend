package com.nidus.twinly.support;

import com.nidus.twinly.legal.repository.PolicyRepository.PolicySummary;

import java.time.Instant;

/** 정책 조회는 본문(content)을 읽지 않는 프로젝션을 쓴다. 테스트에서 그 프로젝션을 만들기 위한 픽스처. */
public record TestPolicySummary(Long id, Long policyNameId, Integer version, String url,
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
    public Integer getVersion() {
        return version;
    }

    @Override
    public String getUrl() {
        return url;
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
