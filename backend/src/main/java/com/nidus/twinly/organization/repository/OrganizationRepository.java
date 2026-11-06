package com.nidus.twinly.organization.repository;

import com.nidus.twinly.organization.domain.OrganizationType;
import com.nidus.twinly.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    List<Organization> findAllByOrderByNameAsc();

    List<Organization> findAllByTypeOrderByNameAsc(OrganizationType type);

    Optional<Organization> findByTypeAndName(OrganizationType type, String name);
}
