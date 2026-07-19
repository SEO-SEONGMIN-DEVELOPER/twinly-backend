package com.nidus.twinly.activity.repository;

import com.nidus.twinly.activity.entity.Scene;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SceneRepository extends JpaRepository<Scene, Long> {

    List<Scene> findAllByUserIdAndDate(Long userId, LocalDate date);

    @Query(value = """
            SELECT DISTINCT s.date
            FROM scenes s
            JOIN scene_partners sp ON sp.scene_id = s.id
            WHERE s.user_id = :userId
              AND sp.user_id = :partnerUserId
              AND (:cursor IS NULL OR s.date < :cursor)
            ORDER BY s.date DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<LocalDate> findDistinctDatesFromCursorByUserIdAndWithPartnerUserId(@Param("userId") Long userId,
                                                                            @Param("partnerUserId") Long partnerUserId,
                                                                            @Param("cursor") LocalDate cursor,
                                                                            @Param("limit") Integer limit);

    @Query(value = """
            SELECT s.*
            FROM scenes s
            JOIN scene_partners sp ON sp.scene_id = s.id
            WHERE s.user_id = :userId
              AND sp.user_id = :partnerUserId
              AND s.date IN (:dates)
            ORDER BY s.date DESC, s.starts_at ASC, s.id ASC
            """, nativeQuery = true)
    List<Scene> findAllByUserIdAndWithPartnerUserIdAndDateIn(@Param("userId") Long userId,
                                                             @Param("partnerUserId") Long partnerUserId,
                                                             @Param("dates") List<LocalDate> dates);
}
