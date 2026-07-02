package com.nidus.twinly.onboarding.service;

import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.common.crypto.AesGcmEncryptor;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.onboarding.dto.OnboardingBasicInfoCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock
    AnonSessionRepository anonSessionRepository;

    @Mock
    AesGcmEncryptor aesGcmEncryptor;

    @InjectMocks
    OnboardingService onboardingService;

    AnonSession anonSession;
    OnboardingBasicInfoCommand onboardingBasicInfoCommand;

    @BeforeEach
    void setUp() throws Exception {
        anonSession = AnonSession.create(UUID.randomUUID(), Instant.now());
        onboardingBasicInfoCommand = new OnboardingBasicInfoCommand("familyName", "givenName", "MALE", "affiliation", "affiliationNumber", "experience", "birthDate");

        when(aesGcmEncryptor.encrypt(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void basicInfo_saves_all_given_info_to_anonSession() throws Exception {
        onboardingService.basicInfo(anonSession, onboardingBasicInfoCommand);

        ArgumentCaptor<AnonSession> captor = ArgumentCaptor.forClass(AnonSession.class);
        verify(anonSessionRepository).save(captor.capture());

        AnonSession saved = captor.getValue();

        assertThat(saved.familyName()).isEqualTo("familyName");
        assertThat(saved.givenName()).isEqualTo("givenName");
        assertThat(saved.gender()).isEqualTo(Gender.MALE);
        assertThat(saved.affiliation()).isEqualTo("affiliation");
        assertThat(saved.affiliationNumber()).isEqualTo("affiliationNumber");
        assertThat(saved.experience()).isEqualTo("experience");
        assertThat(saved.birthDate()).isEqualTo("birthDate");
    }

    @Test
    void basicInfo_saves_a_session() throws Exception {
        onboardingService.basicInfo(anonSession, onboardingBasicInfoCommand);

        verify(anonSessionRepository).save(any(AnonSession.class));
    }
}
