package com.nidus.twinly.school.repository;

import com.nidus.twinly.school.entity.School;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SchoolRepository extends JpaRepository<School, Long> {

    List<School> findAllByOrderByNameAsc();

    Optional<School> findByDomain(String domain);
}
