package com.nidus.twinly.legal.repository;

import com.nidus.twinly.legal.entity.Policy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PolicyRepository extends JpaRepository<Policy, Long> {

    List<PolicySummary> findAllProjectedByPolicyNameIdIn(List<Long> policyNameIds);

    interface PolicySummary {
        Long getId();

        Long getPolicyNameId();

        Integer getVersion();

        String getUrl();

        Boolean getIsRequired();

        Instant getEffectiveAt();
    }
}
