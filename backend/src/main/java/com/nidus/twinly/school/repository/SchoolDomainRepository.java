package com.nidus.twinly.school.repository;

import com.nidus.twinly.school.entity.School;
import com.nidus.twinly.school.entity.SchoolDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SchoolDomainRepository extends JpaRepository<SchoolDomain, Long> {

    @Query("""
            SELECT s FROM School s, SchoolDomain d
            WHERE d.schoolId = s.id AND d.domain = :domain
            """)
    Optional<School> findSchoolByDomain(@Param("domain") String domain);
}
