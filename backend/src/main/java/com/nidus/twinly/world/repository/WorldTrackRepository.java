package com.nidus.twinly.world.repository;

import com.nidus.twinly.world.entity.WorldTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;

public interface WorldTrackRepository extends JpaRepository<WorldTrack, Long> {

    Optional<WorldTrack> findByPoolNumberAndDateAndSectorCodeAndViewerUserId(Integer poolNumber, LocalDate date, String sectorCode, Long viewerUserId);

    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO world_tracks (pool_number, date, sector_code, viewer_user_id, body, received_at)
            VALUES (:poolNumber, :date, :sectorCode, :viewerUserId, :body, UTC_TIMESTAMP(6)) AS new
            ON DUPLICATE KEY UPDATE body = new.body,
                                    received_at = new.received_at
            """, nativeQuery = true)
    void upsert(@Param("poolNumber") int poolNumber,
                @Param("date") LocalDate date,
                @Param("sectorCode") String sectorCode,
                @Param("viewerUserId") long viewerUserId,
                @Param("body") byte[] body);

    @Modifying
    @Query("DELETE FROM WorldTrack t WHERE t.viewerUserId IN :viewerUserIds")
    void deleteAllByViewerUserIdIn(@Param("viewerUserIds") Collection<Long> viewerUserIds);
}
