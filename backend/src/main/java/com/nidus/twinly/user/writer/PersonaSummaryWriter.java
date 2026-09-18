package com.nidus.twinly.user.writer;

import com.nidus.twinly.aichat.event.AiChatCompletedEvent;
import com.nidus.twinly.auth.event.UserSignedUpEvent;
import com.nidus.twinly.common.logging.WarnLog;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.generator.PersonaSummaryGenerator;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Component
@RequiredArgsConstructor
public class PersonaSummaryWriter {

    private final PersonaSummaryGenerator personaSummaryGenerator;

    private final UserRepository userRepository;
    private final PersonaElementRepository personaElementRepository;

    @Async("personaSummaryTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserSignedUp(UserSignedUpEvent event) {
        writeSummary(event.userId(), "가입 후 페르소나 요약을 생성하지 못했습니다.");
    }

    @Async("personaSummaryTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAiChatCompleted(AiChatCompletedEvent event) {
        writeSummary(event.userId(), "AI 대화 종료 후 페르소나 요약을 생성하지 못했습니다.");
    }

    private void writeSummary(Long userId, String failureMessage) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }

        List<PersonaElement> personaElements = personaElementRepository.findAllByUserIdOrderByIdAsc(userId);
        if (personaElements.isEmpty()
                || personaElements.stream().anyMatch(element -> element.getDimension() == PersonaDimension.SUMMARY)) {
            return;
        }

        String summary;
        try {
            summary = personaSummaryGenerator.generate(user.getAffiliation(), personaElements);
        } catch (BusinessException e) {
            WarnLog.log(log, failureMessage, e, field("userId", userId));
            return;
        }

        personaElementRepository.save(PersonaElement.create(userId, PersonaDimension.SUMMARY, summary, Instant.now()));
    }
}
