package com.nidus.twinly.notification.repository;

import com.nidus.twinly.notification.entity.AppNotificationSchedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AppNotificationScheduleRepository extends JpaRepository<AppNotificationSchedule, Long> {

    List<AppNotificationSchedule> findAllBySentAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AppNotificationSchedule> findWithLockById(Long id);

    @Modifying
    @Query(value = """
            INSERT INTO app_notification_schedules (user_id, partner_user_id, type, simulation_date, scheduled_at, created_at)
            VALUES (:userId, :partnerUserId, :type, :simulationDate, :scheduledAt, UTC_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    void insertIfAbsent(@Param("userId") Long userId,
                        @Param("partnerUserId") Long partnerUserId,
                        @Param("type") String type,
                        @Param("simulationDate") LocalDate simulationDate,
                        @Param("scheduledAt") Instant scheduledAt);

    @Modifying
    @Query("""
            DELETE FROM AppNotificationSchedule s
            WHERE s.userId = :userId AND s.simulationDate = :simulationDate AND s.sentAt IS NULL
            """)
    void deleteAllUnsentByUserIdAndSimulationDate(@Param("userId") Long userId,
                                                  @Param("simulationDate") LocalDate simulationDate);

    @Modifying
    @Query("DELETE FROM AppNotificationSchedule s WHERE s.userId IN :userIds")
    void deleteAllByUserIdIn(@Param("userIds") List<Long> userIds);
}
