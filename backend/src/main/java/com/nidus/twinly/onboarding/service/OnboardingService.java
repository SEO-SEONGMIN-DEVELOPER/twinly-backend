package com.nidus.twinly.onboarding.service;

import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.common.crypto.AesGcmEncryptor;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.onboarding.dto.OnboardingBasicInfoCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final AnonSessionRepository anonSessionRepository;

    private final AesGcmEncryptor aesGcmEncryptor;

    public void basicInfo(AnonSession anonSession, OnboardingBasicInfoCommand command) throws Exception {
        if (command.familyName() != null) {
            anonSession = anonSession.withFamilyName(command.familyName());
        }
        if (command.givenName() != null) {
            anonSession = anonSession.withGivenName(command.givenName());
        }
        if (command.gender() != null) {
            anonSession = anonSession.withGender(Gender.valueOf(command.gender()));
        }
        if (command.affiliation() != null) {
            anonSession = anonSession.withAffiliation(aesGcmEncryptor.encrypt(command.affiliation()));
        }
        if (command.affiliationNumber() != null) {
            anonSession = anonSession.withAffiliationNumber(aesGcmEncryptor.encrypt(command.affiliationNumber()));
        }
        if (command.experience() != null) {
            anonSession = anonSession.withExperience(aesGcmEncryptor.encrypt(command.experience()));
        }
        if (command.birthDate() != null) {
            anonSession = anonSession.withBirthDate(aesGcmEncryptor.encrypt(String.valueOf(command.birthDate())));
        }

        anonSessionRepository.save(anonSession);
    }
}
