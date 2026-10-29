package com.nidus.twinly.activity.repository;

import com.nidus.twinly.activity.entity.UserRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserRoomRepository extends JpaRepository<UserRoom, Long> {

    Optional<UserRoom> findByUserId(Long userId);

    @Modifying
    @Query(value = """
            INSERT INTO user_rooms (user_id, room, date, created_at)
            VALUES (:userId, CAST(:room AS JSON), :date, UTC_TIMESTAMP(6)) AS new
            ON DUPLICATE KEY UPDATE room = IF(new.date >= user_rooms.date, new.room, user_rooms.room),
                                    date = GREATEST(user_rooms.date, new.date)
            """, nativeQuery = true)
    void upsertIfNotOlder(@Param("userId") Long userId,
                          @Param("room") String room,
                          @Param("date") LocalDate date);

    @Modifying
    @Query("DELETE FROM UserRoom r WHERE r.userId IN :userIds")
    void deleteAllByUserIdIn(@Param("userIds") List<Long> userIds);
}
