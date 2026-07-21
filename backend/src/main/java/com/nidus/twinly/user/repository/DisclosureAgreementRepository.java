package com.nidus.twinly.user.repository;

import com.nidus.twinly.user.domain.DisclosureField;
import com.nidus.twinly.user.entity.DisclosureAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DisclosureAgreementRepository extends JpaRepository<DisclosureAgreement, Long> {

    List<DisclosureAgreement> findAllByUserId(Long userId);

    boolean existsByUserIdAndField(Long userId, DisclosureField field);

    long deleteByUserIdAndField(Long userId, DisclosureField field);
}