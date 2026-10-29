package com.nidus.twinly.showcase.repository;

import com.nidus.twinly.showcase.entity.ShowcaseUserRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShowcaseUserRefRepository extends JpaRepository<ShowcaseUserRef, Long> {

    List<ShowcaseUserRef> findAllByShowcaseId(Long showcaseId);

    @Modifying
    @Query("DELETE FROM ShowcaseUserRef r WHERE r.showcaseId = :showcaseId")
    void deleteAllByShowcaseId(@Param("showcaseId") Long showcaseId);
}
