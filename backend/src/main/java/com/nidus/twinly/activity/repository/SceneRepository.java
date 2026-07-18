package com.nidus.twinly.activity.repository;

import com.nidus.twinly.activity.entity.Scene;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SceneRepository extends JpaRepository<Scene, Long> {

    List<Scene> findAllByUserIdAndDate(Long userId, LocalDate date);
}
