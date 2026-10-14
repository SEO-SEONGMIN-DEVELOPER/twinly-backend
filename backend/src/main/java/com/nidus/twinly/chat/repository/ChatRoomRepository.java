package com.nidus.twinly.chat.repository;

import com.nidus.twinly.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByMatchId(Long matchId);

    List<ChatRoom> findAllByMatchIdIn(List<Long> matchIds);

    @Query("SELECT r.commonPoint FROM ChatRoom r WHERE r.id = :roomId")
    Optional<String> findCommonPointById(@Param("roomId") Long roomId);

    @Modifying
    @Transactional
    @Query("UPDATE ChatRoom r SET r.commonPoint = :commonPoint WHERE r.id = :roomId AND r.commonPoint IS NULL")
    int assignCommonPointIfAbsent(@Param("roomId") Long roomId, @Param("commonPoint") String commonPoint);

    @Modifying
    @Query(value = """
            DELETE r
            FROM chat_rooms r
            JOIN matches m ON m.id = r.match_id
            WHERE m.user_a_id IN (:userIds)
              AND m.user_b_id IN (:userIds)
            """, nativeQuery = true)
    void deleteAllByMatchBetweenUserIdsIn(@Param("userIds") List<Long> userIds);
}
