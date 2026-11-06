package com.nidus.twinly.invite.writer;

import com.nidus.twinly.aichat.event.AiChatCompletedEvent;
import com.nidus.twinly.invite.entity.TwinCreation;
import com.nidus.twinly.invite.event.InviteCompletedEvent;
import com.nidus.twinly.invite.repository.InviteRegistrationRepository;
import com.nidus.twinly.invite.repository.TwinCreationRepository;
import com.nidus.twinly.twinupdate.service.TwinUpdateReader;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class TwinCreationWriter {

    private final UserRepository userRepository;
    private final TwinUpdateReader twinUpdateReader;
    private final TwinCreationRepository twinCreationRepository;
    private final InviteRegistrationRepository inviteRegistrationRepository;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    public void onAiChatCompleted(AiChatCompletedEvent event) {
        Long userId = event.userId();
        if (twinCreationRepository.existsByUserId(userId)) {
            return;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !twinUpdateReader.isSurveyAndInterestsCompleted(userId)) {
            return;
        }

        Instant now = Instant.now();
        twinCreationRepository.save(TwinCreation.record(userId, user.getDiHash(), now));

        inviteRegistrationRepository.findByInviteeUserId(userId)
                .filter(registration -> !registration.isCompleted())
                .ifPresent(registration -> {
                    registration.complete(now);
                    eventPublisher.publishEvent(new InviteCompletedEvent(registration.getInviterUserId()));
                });
    }
}
