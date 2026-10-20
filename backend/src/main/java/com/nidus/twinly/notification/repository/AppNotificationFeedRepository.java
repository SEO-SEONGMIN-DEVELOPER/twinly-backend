package com.nidus.twinly.notification.repository;

import com.nidus.twinly.notification.domain.AppNotificationFeedType;
import com.nidus.twinly.notification.entity.AppNotificationFeed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AppNotificationFeedRepository extends JpaRepository<AppNotificationFeed, Long> {

    int countByUserIdAndReadAtIsNull(Long userId);

    Optional<AppNotificationFeed> findByIdAndUserId(Long id, Long userId);

    void deleteAllByUserIdAndTypeAndTargetUserIdAndSimulationDate(Long userId, AppNotificationFeedType type,
                                                                  Long targetUserId, LocalDate simulationDate);

    @Query(value = """
            SELECT f.*
            FROM app_notification_feeds f
            WHERE f.user_id = :userId
              AND (:unreadOnly = FALSE OR f.read_at IS NULL)
              AND (:type IS NULL OR f.type = :type)
            ORDER BY f.created_at DESC, f.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<AppNotificationFeed> findAllByUserIdAndFilter(@Param("userId") Long userId,
                                                       @Param("unreadOnly") Boolean unreadOnly,
                                                       @Param("type") String type,
                                                       @Param("limit") Integer limit);

    @Query("SELECT COALESCE(MAX(f.id), 0) FROM AppNotificationFeed f")
    Long findMaxId();

    List<AppNotificationFeed> findAllByIdGreaterThanAndTypeAndTitleAndBody(Long id, AppNotificationFeedType type,
                                                                          String title, String body);

    @Modifying
    @Query(value = """
            INSERT INTO app_notification_feeds (user_id, title, body, type)
            SELECT u.id, :title, :body, 'NOTICE'
            FROM users u
            WHERE u.deleted_at IS NULL
              AND u.withdrawal_requested_at IS NULL
              AND u.is_seed = FALSE
              AND NOT EXISTS (SELECT 1
                              FROM app_notification_feeds f
                              WHERE f.user_id = u.id
                                AND f.type = 'NOTICE'
                                AND f.title = :title
                                AND f.body = :body)
            """, nativeQuery = true)
    int insertNoticeForActiveUsers(@Param("title") String title, @Param("body") String body);

    @Modifying
    @Query(value = """
            INSERT INTO app_notification_feeds (user_id, title, body, type)
            SELECT u.id, :title, :body, 'NOTICE'
            FROM users u
            WHERE u.id IN (:userIds)
              AND u.deleted_at IS NULL
              AND u.withdrawal_requested_at IS NULL
              AND u.is_seed = FALSE
              AND NOT EXISTS (SELECT 1
                              FROM app_notification_feeds f
                              WHERE f.user_id = u.id
                                AND f.type = 'NOTICE'
                                AND f.title = :title
                                AND f.body = :body)
            """, nativeQuery = true)
    int insertNoticeForActiveUsersIn(@Param("title") String title, @Param("body") String body,
                                     @Param("userIds") List<Long> userIds);

    @Query(value = """
            SELECT COUNT(*)
            FROM users u
            WHERE u.deleted_at IS NULL
              AND u.withdrawal_requested_at IS NULL
              AND u.is_seed = FALSE
            """, nativeQuery = true)
    long countNoticeRecipients();

    @Query(value = """
            SELECT COUNT(*)
            FROM users u
            WHERE u.id IN (:userIds)
              AND u.deleted_at IS NULL
              AND u.withdrawal_requested_at IS NULL
              AND u.is_seed = FALSE
            """, nativeQuery = true)
    long countNoticeRecipientsIn(@Param("userIds") List<Long> userIds);

    @Modifying
    @Query("DELETE FROM AppNotificationFeed f WHERE f.userId IN :userIds AND f.type = :type")
    void deleteAllByUserIdInAndType(@Param("userIds") List<Long> userIds, @Param("type") AppNotificationFeedType type);

    @Modifying
    @Query(value = """
            UPDATE app_notification_feeds
            SET read_at = UTC_TIMESTAMP(6)
            WHERE user_id = :userId
              AND id <= :lastAppNotificationId
              AND read_at IS NULL
            """, nativeQuery = true)
    void markAllReadByUserIdAndIdLessThanEqual(@Param("userId") Long userId,
                                               @Param("lastAppNotificationId") Long lastAppNotificationId);

    @Modifying
    @Query(value = """
            DELETE f
            FROM app_notification_feeds f
            JOIN chat_rooms r ON r.id = f.target_chat_room_id
            JOIN matches m ON m.id = r.match_id
            WHERE m.user_a_id IN (:userIds)
              AND m.user_b_id IN (:userIds)
            """, nativeQuery = true)
    void deleteAllByTargetChatRoomBetweenUserIdsIn(@Param("userIds") List<Long> userIds);
}
