package com.nidus.twinly.school.repository;

import com.nidus.twinly.school.entity.SchoolAffiliation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SchoolAffiliationRepository extends JpaRepository<SchoolAffiliation, Long> {

    List<SchoolAffiliation> findAllBySchoolIdOrderByNameAsc(Long schoolId);
}
