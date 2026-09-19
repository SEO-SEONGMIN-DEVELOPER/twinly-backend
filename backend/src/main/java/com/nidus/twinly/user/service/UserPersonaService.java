package com.nidus.twinly.user.service;

import com.nidus.twinly.aichat.repository.AiChatRepository;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.UserRepository;
import com.nidus.twinly.user.repository.UserSurveyAnswerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserPersonaService {

    private final UserRepository userRepository;
    private final PersonaElementRepository personaElementRepository;
    private final UserSurveyAnswerRepository userSurveyAnswerRepository;
    private final AiChatRepository aiChatRepository;

    @Transactional
    public void resetPersona(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        personaElementRepository.deleteAllByUserId(userId);
        userSurveyAnswerRepository.deleteAllByUserId(userId);
        aiChatRepository.deleteAllByUserId(userId);
        userRepository.clearAiChatCompleted(userId);
    }
}
