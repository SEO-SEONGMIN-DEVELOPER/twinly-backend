package com.nidus.twinly.organization.repository;

import com.nidus.twinly.organization.entity.OrganizationAffiliation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrganizationAffiliationRepository extends JpaRepository<OrganizationAffiliation, Long> {

    List<OrganizationAffiliation> findAllByOrganizationIdOrderByNameAsc(Long organizationId);
}
