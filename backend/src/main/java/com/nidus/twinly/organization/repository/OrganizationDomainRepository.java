package com.nidus.twinly.organization.repository;

import com.nidus.twinly.organization.entity.Organization;
import com.nidus.twinly.organization.entity.OrganizationDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrganizationDomainRepository extends JpaRepository<OrganizationDomain, Long> {

    @Query("""
            SELECT o FROM Organization o, OrganizationDomain d
            WHERE d.organizationId = o.id AND d.domain = :domain
            """)
    Optional<Organization> findOrganizationByDomain(@Param("domain") String domain);
}
