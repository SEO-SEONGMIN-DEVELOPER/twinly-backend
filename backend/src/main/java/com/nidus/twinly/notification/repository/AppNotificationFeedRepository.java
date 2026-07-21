package com.nidus.twinly.notification.repository;

import com.nidus.twinly.notification.entity.AppNotificationFeed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppNotificationFeedRepository extends JpaRepository<AppNotificationFeed, Long> {

    int countByUserIdAndReadAtIsNull(Long userId);

    Optional<AppNotificationFeed> findByIdAndUserId(Long id, Long userId);

    @Query(value = """
            SELECT f.*
            FROM app_notification_feeds f
            WHERE f.user_id = :userId
              AND (:unreadOnly = FALSE OR f.read_at IS NULL)
              AND (:type IS NULL OR f.type = CAST(:type AS APP_NOTIFICATION_FEED_TYPE))
            ORDER BY f.created_at DESC, f.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<AppNotificationFeed> findAllByUserIdAndFilter(@Param("userId") Long userId,
                                                       @Param("unreadOnly") Boolean unreadOnly,
                                                       @Param("type") String type,
                                                       @Param("limit") Integer limit);

    @Modifying
    @Query(value = """
            UPDATE app_notification_feeds
            SET read_at = now()
            WHERE user_id = :userId
              AND id <= :lastAppNotificationId
              AND read_at IS NULL
            """, nativeQuery = true)
    void markAllReadByUserIdAndIdLessThanEqual(@Param("userId") Long userId,
                                               @Param("lastAppNotificationId") Long lastAppNotificationId);
}
