package com.nidus.twinly.anon.repository;

import com.nidus.twinly.common.domain.PhotoType;
import com.nidus.twinly.anon.entity.AnonSessionPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnonSessionPhotoRepository extends JpaRepository<AnonSessionPhoto, Long> {
    Optional<AnonSessionPhoto> findByAnonSessionIdAndType(Long anonSessionId, PhotoType type);
}