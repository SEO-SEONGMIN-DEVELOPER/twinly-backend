package com.nidus.twinly.match.repository;

import com.nidus.twinly.match.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, Long> {

    Optional<Match> findByUserAIdAndUserBId(Long userAId, Long userBId);

    List<Match> findAllByUserAIdOrUserBId(Long userAId, Long userBId);

    @Query(value = """
            SELECT m.*
            FROM matches m
            WHERE (m.user_a_id = :userId AND m.user_b_id IN (:partnerUserIds))
               OR (m.user_b_id = :userId AND m.user_a_id IN (:partnerUserIds))
            """, nativeQuery = true)
    List<Match> findAllByUserIdAndPartnerUserIdIn(@Param("userId") Long userId,
                                                  @Param("partnerUserIds") List<Long> partnerUserIds);
}
