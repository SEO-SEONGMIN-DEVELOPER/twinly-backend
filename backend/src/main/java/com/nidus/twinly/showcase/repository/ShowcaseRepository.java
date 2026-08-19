package com.nidus.twinly.showcase.repository;

import com.nidus.twinly.showcase.entity.Showcase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ShowcaseRepository extends JpaRepository<Showcase, Long> {

    Optional<Showcase> findByViewerUserIdAndDate(Long viewerUserId, LocalDate date);

    @Query(value = """
            SELECT DISTINCT s.user_id
            FROM scenes s
            JOIN season_participations p ON p.user_id = s.user_id AND p.season_id = :seasonId
            JOIN users u ON u.id = s.user_id AND u.deleted_at IS NULL
            WHERE s.date = :date
              AND s.user_id <> :viewerUserId
              AND NOT EXISTS (
                  SELECT 1 FROM blocks b
                  WHERE (b.user_id = :viewerUserId AND b.blocked_user_id = s.user_id)
                     OR (b.user_id = s.user_id AND b.blocked_user_id = :viewerUserId)
              )
            """, nativeQuery = true)
    List<Long> findAllTargetCandidateUserIds(@Param("viewerUserId") Long viewerUserId,
                                             @Param("seasonId") Long seasonId,
                                             @Param("date") LocalDate date);
}
