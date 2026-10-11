package com.nidus.twinly.user.repository;

import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.user.entity.Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

    Optional<Photo> findByUserIdAndType(Long userId, PhotoType type);

    List<Photo> findAllByUserIdInAndType(List<Long> userIds, PhotoType type);

    @Transactional
    @Modifying
    @Query(value = """
            UPDATE photos
            SET preview_key = :previewKey
            WHERE id = :id
              AND `key` = :key
              AND preview_key IS NULL
            """, nativeQuery = true)
    int assignPreviewKey(@Param("id") Long id, @Param("key") String key, @Param("previewKey") String previewKey);
}
