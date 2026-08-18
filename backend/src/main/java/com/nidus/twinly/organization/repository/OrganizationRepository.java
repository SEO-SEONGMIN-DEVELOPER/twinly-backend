package com.nidus.twinly.organization.repository;

import com.nidus.twinly.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    List<Organization> findAllByOrderByNameAsc();
}
