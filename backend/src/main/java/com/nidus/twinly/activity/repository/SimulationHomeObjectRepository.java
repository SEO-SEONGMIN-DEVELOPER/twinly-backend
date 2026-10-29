package com.nidus.twinly.activity.repository;

import com.nidus.twinly.activity.entity.SimulationHomeObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SimulationHomeObjectRepository extends JpaRepository<SimulationHomeObject, Long> {

    Optional<SimulationHomeObject> findByUserIdAndDate(Long userId, LocalDate date);

    @Modifying
    @Query("DELETE FROM SimulationHomeObject h WHERE h.userId = :userId AND h.date = :date")
    void deleteAllByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Modifying
    @Query("DELETE FROM SimulationHomeObject h WHERE h.userId IN :userIds")
    void deleteAllByUserIdIn(@Param("userIds") List<Long> userIds);
}
