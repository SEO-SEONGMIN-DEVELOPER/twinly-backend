package com.nidus.twinly.invite.repository;

import com.nidus.twinly.invite.entity.TwinCreation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface TwinCreationRepository extends JpaRepository<TwinCreation, Long> {

    boolean existsByUserId(Long userId);

    boolean existsByDiHash(String diHash);

    @Modifying
    @Query(value = """
            UPDATE twin_creations t
            JOIN users u ON u.id = t.user_id
            SET t.di_hash = NULL
            WHERE t.di_hash IS NOT NULL
              AND u.deleted_at IS NOT NULL
              AND COALESCE(u.withdrawal_requested_at, u.deleted_at) < :cutoff
            """, nativeQuery = true)
    int clearDiHashOfWithdrawnBefore(@Param("cutoff") Instant cutoff);
}
