package com.nidus.twinly.anon.repository;

import com.nidus.twinly.anon.entity.AnonSessionPersonaElement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnonSessionPersonaElementRepository extends JpaRepository<AnonSessionPersonaElement, Long> {

    List<AnonSessionPersonaElement> findAllByAnonSessionId(Long anonSessionId);
}