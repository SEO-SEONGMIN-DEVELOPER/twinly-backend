package com.nidus.twinly.people.repository;

import com.nidus.twinly.people.entity.TwinView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TwinViewRepository extends JpaRepository<TwinView, Long> {

    @Query(value = """
            SELECT
                v.target_user_id AS targetUserId
                , COUNT(DISTINCT v.viewer_user_id) AS viewerCount
            FROM twin_views v
            WHERE v.viewed_at >= :from AND v.viewed_at < :to
            GROUP BY v.target_user_id
            """, nativeQuery = true)
    List<ViewerCountProjection> countDistinctViewersByViewedAtRange(@Param("from") Instant from,
                                                                   @Param("to") Instant to);

    interface ViewerCountProjection {
        Long getTargetUserId();
        Long getViewerCount();
    }
}
