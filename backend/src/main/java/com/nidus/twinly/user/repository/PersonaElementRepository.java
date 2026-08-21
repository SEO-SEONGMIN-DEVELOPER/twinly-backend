package com.nidus.twinly.user.repository;

import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.user.entity.PersonaElement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersonaElementRepository extends JpaRepository<PersonaElement, Long> {

    List<PersonaElement> findAllByUserIdOrderByIdAsc(Long userId);

    boolean existsByUserId(Long userId);

    boolean existsByUserIdAndDimension(Long userId, PersonaDimension dimension);
}
