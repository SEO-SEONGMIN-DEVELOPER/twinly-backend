package com.nidus.twinly.legal.repository;

import com.nidus.twinly.legal.entity.PolicyName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PolicyNameRepository extends JpaRepository<PolicyName, Long> {

    List<PolicyName> findAllByIsDeprecatedFalse();
}
