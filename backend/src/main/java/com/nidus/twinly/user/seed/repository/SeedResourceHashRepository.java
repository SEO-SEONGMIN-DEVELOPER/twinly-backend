package com.nidus.twinly.user.seed.repository;

import com.nidus.twinly.user.seed.entity.SeedResourceHash;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeedResourceHashRepository extends JpaRepository<SeedResourceHash, Long> {

    Optional<SeedResourceHash> findByResource(String resource);
}
