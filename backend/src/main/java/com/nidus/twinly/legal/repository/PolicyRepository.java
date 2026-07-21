package com.nidus.twinly.legal.repository;

import com.nidus.twinly.legal.entity.Policy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PolicyRepository extends JpaRepository<Policy, Long> {

    List<Policy> findAllByPolicyNameIdIn(List<Long> policyNameIds);
}
