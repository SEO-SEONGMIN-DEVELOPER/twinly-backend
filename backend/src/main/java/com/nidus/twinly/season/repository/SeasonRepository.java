package com.nidus.twinly.season.repository;

import com.nidus.twinly.season.entity.Season;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeasonRepository extends JpaRepository<Season, Long> {

    Optional<Season> findFirstByIsActiveTrueOrderByIdDesc();

    List<Season> findAllByIsActiveTrue();
}
