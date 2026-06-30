package com.nidus.twinly.anon.service;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.anon.service.AnonService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AnonServiceTest {

    AnonSessionRepository anonSessionRepository = mock(AnonSessionRepository.class);
    AnonService anonService = new AnonService(anonSessionRepository);

    @Test
    void start_generates_unique_token_each_call() {
        UUID token1 = anonService.start().anonSessionToken();
        UUID token2 = anonService.start().anonSessionToken();

        assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    void start_saves_a_session() {
        anonService.start();

        verify(anonSessionRepository).save(any(AnonSession.class));
    }
}