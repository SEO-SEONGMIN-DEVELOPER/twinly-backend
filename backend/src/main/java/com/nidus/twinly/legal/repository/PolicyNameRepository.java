package com.nidus.twinly.legal.repository;

import com.nidus.twinly.legal.domain.PolicyKind;
import com.nidus.twinly.legal.entity.PolicyName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PolicyNameRepository extends JpaRepository<PolicyName, Long> {

    List<PolicyName> findAllByIsDeprecatedFalseOrderByIdAsc();

    List<PolicyName> findAllByKindAndIsDeprecatedFalseOrderByIdAsc(PolicyKind kind);

    List<PolicyName> findAllByIdentifierIn(List<String> identifiers);
}
