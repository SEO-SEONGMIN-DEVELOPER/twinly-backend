package com.nidus.twinly.organization.repository;

import com.nidus.twinly.organization.entity.Organization;
import com.nidus.twinly.organization.entity.OrganizationDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrganizationDomainRepository extends JpaRepository<OrganizationDomain, Long> {

    @Query("""
            SELECT o FROM Organization o, OrganizationDomain d
            WHERE d.organizationId = o.id AND d.domain = :domain
            """)
    Optional<Organization> findOrganizationByDomain(@Param("domain") String domain);

    @Query(value = """
            SELECT o.id, o.name, o.type
            FROM organizations o
            JOIN organization_domains d ON d.organization_id = o.id
            WHERE d.domain = :domain
            FOR SHARE
            """, nativeQuery = true)
    Optional<Organization> findOrganizationByDomainForShare(@Param("domain") String domain);

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO organization_domains (organization_id, domain)
            VALUES (:organizationId, :domain)
            """, nativeQuery = true)
    int insertIgnore(@Param("organizationId") Long organizationId, @Param("domain") String domain);
}
