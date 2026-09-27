package com.nidus.twinly.purchase.writer;

import com.nidus.twinly.purchase.entity.EarlySignupGrant;
import com.nidus.twinly.purchase.entity.EarlySignupGrantCounter;
import com.nidus.twinly.purchase.repository.EarlySignupGrantCounterRepository;
import com.nidus.twinly.purchase.repository.EarlySignupGrantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class EarlySignupGrantWriter {

    private final EarlySignupGrantCounterRepository earlySignupGrantCounterRepository;
    private final EarlySignupGrantRepository earlySignupGrantRepository;

    @Transactional
    public void assign(Long userId, String diHash, Instant assignedAt) {
        EarlySignupGrantCounter counter = earlySignupGrantCounterRepository.findWithLockById(EarlySignupGrantCounter.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("early_signup_grant_counter 행이 없습니다."));

        if (counter.isFull() || earlySignupGrantRepository.existsByDiHash(diHash)) {
            return;
        }

        earlySignupGrantRepository.save(EarlySignupGrant.assign(userId, diHash, assignedAt));
        counter.increase();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markGranted(Long grantId, Instant grantedAt) {
        earlySignupGrantRepository.markGranted(grantId, grantedAt);
    }
}
