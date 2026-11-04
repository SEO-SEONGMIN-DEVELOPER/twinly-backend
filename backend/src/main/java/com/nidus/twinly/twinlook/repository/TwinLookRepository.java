package com.nidus.twinly.twinlook.repository;

import com.nidus.twinly.twinlook.entity.TwinLook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TwinLookRepository extends JpaRepository<TwinLook, Long> {

    Optional<TwinLook> findByUserId(Long userId);

    List<TwinLook> findAllByUserIdIn(Collection<Long> userIds);

    @Modifying
    @Query(value = """
            INSERT INTO twin_looks (user_id, hair, hair_color, outfit, outfit_color)
            VALUES (:userId, :hair, :hairColor, :outfit, :outfitColor) AS new
            ON DUPLICATE KEY UPDATE hair = new.hair,
                                    hair_color = new.hair_color,
                                    outfit = new.outfit,
                                    outfit_color = new.outfit_color,
                                    updated_at = UTC_TIMESTAMP(6)
            """, nativeQuery = true)
    void upsert(@Param("userId") Long userId,
                @Param("hair") int hair,
                @Param("hairColor") int hairColor,
                @Param("outfit") int outfit,
                @Param("outfitColor") int outfitColor);

    @Modifying
    @Query("DELETE FROM TwinLook t WHERE t.userId IN :userIds")
    void deleteAllByUserIdIn(@Param("userIds") List<Long> userIds);
}
