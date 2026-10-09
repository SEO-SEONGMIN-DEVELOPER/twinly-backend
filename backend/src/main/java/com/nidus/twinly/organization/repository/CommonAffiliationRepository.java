package com.nidus.twinly.organization.repository;

import com.nidus.twinly.organization.entity.CommonAffiliation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommonAffiliationRepository extends JpaRepository<CommonAffiliation, Long> {

    List<CommonAffiliation> findAllByOrderByNameAsc();
}
