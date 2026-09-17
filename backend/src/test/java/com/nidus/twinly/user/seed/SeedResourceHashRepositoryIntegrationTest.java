package com.nidus.twinly.user.seed;

import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.seed.entity.SeedResourceHash;
import com.nidus.twinly.user.seed.repository.SeedResourceHashRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeedResourceHashRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final String RESOURCE = "seed/showcase-scenarios.json";

    @Autowired
    SeedResourceHashRepository seedResourceHashRepository;

    @Test
    @DisplayName("시드 파일 이름으로 저장한 해시를 다시 찾고, 바꾼 해시가 그대로 반영된다")
    void saves_finds_and_changes_hash_by_resource() {
        // given: 시드 파일 해시 한 건 저장
        seedResourceHashRepository.saveAndFlush(SeedResourceHash.create(RESOURCE, "a".repeat(64), Instant.now()));

        // when: 이름으로 찾아 해시를 바꿔 저장
        SeedResourceHash stored = seedResourceHashRepository.findByResource(RESOURCE).orElseThrow();
        stored.change("b".repeat(64), Instant.now());
        seedResourceHashRepository.saveAndFlush(stored);

        // then: 다시 찾으면 바뀐 해시이고, 없는 이름은 비어 있다
        assertThat(seedResourceHashRepository.findByResource(RESOURCE).orElseThrow().getHash()).isEqualTo("b".repeat(64));
        assertThat(seedResourceHashRepository.findByResource("seed/unknown.json")).isEmpty();
    }

    @Test
    @DisplayName("같은 시드 파일 이름으로 두 번 저장하면 유일 제약에 막힌다")
    void rejects_duplicate_resource() {
        // given: 시드 파일 해시 한 건 저장
        seedResourceHashRepository.saveAndFlush(SeedResourceHash.create(RESOURCE, "a".repeat(64), Instant.now()));

        // when & then: 같은 이름의 두 번째 행은 저장되지 않는다
        assertThatThrownBy(() -> seedResourceHashRepository.saveAndFlush(SeedResourceHash.create(RESOURCE, "b".repeat(64), Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
