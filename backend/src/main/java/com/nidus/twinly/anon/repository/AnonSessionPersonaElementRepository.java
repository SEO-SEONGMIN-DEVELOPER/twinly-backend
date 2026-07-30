package com.nidus.twinly.anon.repository;

import com.nidus.twinly.anon.entity.AnonSessionPersonaElement;
import com.nidus.twinly.common.persona.PersonaDimension;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AnonSessionPersonaElementRepository extends JpaRepository<AnonSessionPersonaElement, Long> {

    List<AnonSessionPersonaElement> findAllByAnonSessionId(Long anonSessionId);

    @Modifying(clearAutomatically = true)
    @Query("""
            DELETE FROM AnonSessionPersonaElement e
            WHERE e.anonSessionId = :anonSessionId AND e.dimension IN :dimensions
            """)
    void deleteByAnonSessionIdAndDimensionIn(@Param("anonSessionId") Long anonSessionId,
                                             @Param("dimensions") Collection<PersonaDimension> dimensions);
}
