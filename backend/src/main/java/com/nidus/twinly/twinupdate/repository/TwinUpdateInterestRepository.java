package com.nidus.twinly.twinupdate.repository;

import com.nidus.twinly.twinupdate.entity.TwinUpdateInterest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TwinUpdateInterestRepository extends JpaRepository<TwinUpdateInterest, Long> {

    List<TwinUpdateInterest> findAllByTwinUpdateIdOrderByIdAsc(Long twinUpdateId);

    boolean existsByTwinUpdateId(Long twinUpdateId);

    @Modifying
    @Query("DELETE FROM TwinUpdateInterest i WHERE i.twinUpdateId = :twinUpdateId")
    void deleteAllByTwinUpdateId(@Param("twinUpdateId") Long twinUpdateId);
}
