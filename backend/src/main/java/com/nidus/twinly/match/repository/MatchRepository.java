package com.nidus.twinly.match.repository;

import com.nidus.twinly.match.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchRepository extends JpaRepository<Match, Long> {

    List<Match> findAllByUserAIdOrUserBId(Long userAId, Long userBId);
}