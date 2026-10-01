package com.nidus.twinly.activity.repository;

import com.nidus.twinly.activity.entity.ScenePartner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface ScenePartnerRepository extends JpaRepository<ScenePartner, Long> {

    List<ScenePartner> findAllBySceneIdIn(List<Long> sceneIds);

    void deleteAllBySceneIdIn(List<Long> sceneIds);

    @Modifying
    @Query("DELETE FROM ScenePartner sp WHERE sp.sceneId IN (SELECT s.id FROM Scene s WHERE s.userId IN :userIds)")
    void deleteAllBySceneUserIdIn(@Param("userIds") List<Long> userIds);

    boolean existsBySceneIdAndUserId(Long sceneId, Long userId);

    @Query(value = """
            SELECT
                sp.user_id AS partnerUserId 
                , COUNT(*) AS count
            FROM scene_partners sp
            JOIN scenes s 
                ON s.id = sp.scene_id
            WHERE s.user_id = :userId AND sp.user_id IN (:partnerUserIds) AND s.ends_at <= :now
            GROUP BY sp.user_id
            """, nativeQuery = true)
    List<SceneCountProjection> countScenesByUserIdAndPartnerUserIdIn(@Param("userId") Long userId,
                                                               @Param("partnerUserIds") List<Long> partnerUserIds,
                                                               @Param("now") LocalDateTime now);

    @Query(value = """
            SELECT DISTINCT sp.user_id
            FROM scene_partners sp
            JOIN scenes s
                ON s.id = sp.scene_id
            WHERE s.user_id = :userId
              AND sp.user_id IN (:partnerUserIds)
              AND s.type = 'DIALOGUE'
              AND s.date < :date
            """, nativeQuery = true)
    List<Long> findPartnerUserIdsWithDialogueBeforeDate(@Param("userId") Long userId,
                                                        @Param("partnerUserIds") List<Long> partnerUserIds,
                                                        @Param("date") LocalDate date);

    interface SceneCountProjection {
        Long getPartnerUserId();
        Long getCount();
    }
}
