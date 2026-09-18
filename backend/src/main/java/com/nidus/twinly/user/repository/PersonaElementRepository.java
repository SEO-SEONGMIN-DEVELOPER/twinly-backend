package com.nidus.twinly.user.repository;

import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.user.entity.PersonaElement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PersonaElementRepository extends JpaRepository<PersonaElement, Long> {

    List<PersonaElement> findAllByUserIdOrderByIdAsc(Long userId);

    List<PersonaElement> findAllByUserIdAndDimensionOrderByIdAsc(Long userId, PersonaDimension dimension);

    boolean existsByUserId(Long userId);

    boolean existsByUserIdAndDimension(Long userId, PersonaDimension dimension);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM PersonaElement e
            WHERE e.userId = :userId AND e.dimension = :dimension
            """)
    void deleteByUserIdAndDimension(@Param("userId") Long userId,
                                    @Param("dimension") PersonaDimension dimension);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM PersonaElement e
            WHERE e.userId = :userId AND e.dimension IN :dimensions
            """)
    void deleteByUserIdAndDimensionIn(@Param("userId") Long userId,
                                      @Param("dimensions") Collection<PersonaDimension> dimensions);
}
