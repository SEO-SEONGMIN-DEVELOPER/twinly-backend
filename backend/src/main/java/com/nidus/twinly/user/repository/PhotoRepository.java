package com.nidus.twinly.user.repository;

import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.user.entity.Photo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

    Optional<Photo> findByUserIdAndType(Long userId, PhotoType type);
}
