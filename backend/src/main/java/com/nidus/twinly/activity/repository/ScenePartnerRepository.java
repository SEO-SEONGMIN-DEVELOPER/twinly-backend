package com.nidus.twinly.activity.repository;

import com.nidus.twinly.activity.entity.ScenePartner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ScenePartnerRepository extends JpaRepository<ScenePartner, Long> {

    List<ScenePartner> findAllBySceneIdIn(List<Long> sceneIds);

    @Query(value = """
            SELECT
                sp.user_id AS partnerUserId 
                , COUNT(*) AS count
            FROM scene_partners sp
            JOIN scenes s 
                ON s.id = sp.scene_id
            WHERE s.user_id = :userId AND sp.user_id IN (:partnerUserIds)
            GROUP BY sp.user_id
            """, nativeQuery = true)
    List<SceneCountProjection> countScenesByUserIdAndPartnerUserIdIn(@Param("userId") Long userId,
                                                               @Param("partnerUserIds") List<Long> partnerUserIds);

    interface SceneCountProjection {
        Long getPartnerUserId();
        Long getCount();
    }
}
