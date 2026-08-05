package com.nidus.twinly.school.repository;

import com.nidus.twinly.school.entity.School;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SchoolRepository extends JpaRepository<School, Long> {

    List<School> findAllByOrderByNameAsc();
}
