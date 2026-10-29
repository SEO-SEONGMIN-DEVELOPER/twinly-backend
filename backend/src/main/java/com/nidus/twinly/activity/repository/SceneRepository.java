package com.nidus.twinly.activity.repository;

import com.nidus.twinly.activity.domain.SceneType;
import com.nidus.twinly.activity.entity.Scene;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface SceneRepository extends JpaRepository<Scene, Long> {

    @Query("SELECT s.userId AS userId, s.date AS date FROM Scene s WHERE s.userId IN :userIds GROUP BY s.userId, s.date")
    List<SceneDayProjection> findAllDaysByUserIdIn(@Param("userIds") List<Long> userIds);

    List<Scene> findAllByUserIdAndDate(Long userId, LocalDate date);

    List<Scene> findAllByUserIdAndDateOrderByStartsAtAsc(Long userId, LocalDate date);

    boolean existsByUserIdAndDate(Long userId, LocalDate date);

    List<Scene> findAllByUserIdAndDateAndType(Long userId, LocalDate date, SceneType type);

    @Query(value = """
            SELECT DISTINCT s.date
            FROM scenes s
            JOIN scene_partners sp ON sp.scene_id = s.id
            WHERE s.user_id = :userId
              AND sp.user_id = :partnerUserId
              AND s.ends_at <= :now
              AND (:cursor IS NULL OR s.date < :cursor)
            ORDER BY s.date DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<LocalDate> findDistinctDatesFromCursorByUserIdAndWithPartnerUserId(@Param("userId") Long userId,
                                                                            @Param("partnerUserId") Long partnerUserId,
                                                                            @Param("cursor") LocalDate cursor,
                                                                            @Param("limit") Integer limit,
                                                                            @Param("now") LocalDateTime now);

    @Query(value = """
            SELECT s.*
            FROM scenes s
            JOIN scene_partners sp ON sp.scene_id = s.id
            WHERE s.user_id = :userId
              AND sp.user_id = :partnerUserId
              AND s.date IN (:dates)
              AND s.ends_at <= :now
            ORDER BY s.date DESC, s.starts_at ASC, s.id ASC
            """, nativeQuery = true)
    List<Scene> findAllByUserIdAndWithPartnerUserIdAndDateIn(@Param("userId") Long userId,
                                                             @Param("partnerUserId") Long partnerUserId,
                                                             @Param("dates") List<LocalDate> dates,
                                                             @Param("now") LocalDateTime now);

    @Query("""
            SELECT s.id AS id, s.userId AS userId, s.type AS type, s.startsAt AS startsAt, s.endsAt AS endsAt,
                   s.placeCode AS placeCode, s.fromPlaceCode AS fromPlaceCode, s.travelMode AS travelMode
            FROM Scene s
            WHERE s.userId IN :userIds
              AND s.date BETWEEN :fromDate AND :toDate
              AND s.endsAt > :endsAfter
            ORDER BY s.startsAt ASC, s.id ASC
            """)
    List<SceneRouteProjection> findAllRoutesByUserIdInAndEndsAfter(@Param("userIds") List<Long> userIds,
                                                                    @Param("fromDate") LocalDate fromDate,
                                                                    @Param("toDate") LocalDate toDate,
                                                                    @Param("endsAfter") LocalDateTime endsAfter);

    @Query("""
            SELECT s.id AS id, s.type AS type, s.startsAt AS startsAt, s.endsAt AS endsAt,
                   s.place AS place, s.placeCode AS placeCode, s.lines AS lines
            FROM Scene s
            WHERE s.userId = :userId
              AND s.endsAt > :from
              AND s.startsAt < :to
            ORDER BY s.startsAt ASC, s.id ASC
            """)
    List<SceneRecapProjection> findAllRecapByUserIdAndOverlapping(@Param("userId") Long userId,
                                                                  @Param("from") LocalDateTime from,
                                                                  @Param("to") LocalDateTime to);

    @Modifying
    @Query("DELETE FROM Scene s WHERE s.userId IN :userIds")
    void deleteAllByUserIdIn(@Param("userIds") List<Long> userIds);

    interface SceneDayProjection {
        Long getUserId();
        LocalDate getDate();
    }

    interface SceneRecapProjection {
        Long getId();
        SceneType getType();
        LocalDateTime getStartsAt();
        LocalDateTime getEndsAt();
        String getPlace();
        String getPlaceCode();
        String getLines();
    }

    interface SceneRouteProjection {
        Long getId();
        Long getUserId();
        SceneType getType();
        LocalDateTime getStartsAt();
        LocalDateTime getEndsAt();
        String getPlaceCode();
        String getFromPlaceCode();
        String getTravelMode();
    }
}
