package com.nidus.twinly.onboarding.service;

import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.survey.SurveyLoader;
import com.nidus.twinly.common.survey.SurveyOptionName;
import com.nidus.twinly.common.survey.SurveyQuestion;
import com.nidus.twinly.onboarding.dto.command.OnboardingBasicInfoCommand;
import com.nidus.twinly.onboarding.dto.command.OnboardingSurveyAnswerCommand;
import com.nidus.twinly.onboarding.dto.result.OnboardingSurveyAnswerResult;
import com.nidus.twinly.onboarding.entity.PersonaElement;
import com.nidus.twinly.onboarding.entity.SurveyAnswer;
import com.nidus.twinly.onboarding.repository.PersonaElementRepository;
import com.nidus.twinly.onboarding.repository.SurveyAnswerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final AnonSessionRepository anonSessionRepository;
    private final PersonaElementRepository personaElementRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;

    private final SurveyLoader surveyLoader;

    @Transactional
    public void basicInfo(Long anonSessionId, OnboardingBasicInfoCommand command) {
        AnonSession managed = anonSessionRepository.findById(anonSessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 세션입니다"));

        if (command.familyName() != null) {
            managed.changeFamilyName(command.familyName());
        }
        if (command.givenName() != null) {
            managed.changeGivenName(command.givenName());
        }
        if (command.gender() != null) {
            managed.changeGender(Gender.valueOf(command.gender()));
        }
        if (command.affiliation() != null) {
            managed.changeAffiliation(command.affiliation());
        }
        if (command.affiliationNumber() != null) {
            managed.changeAffiliationNumber(command.affiliationNumber());
        }
        if (command.experience() != null) {
            managed.changeExperience(command.experience());
        }
        if (command.birthDate() != null) {
            managed.changeBirthDate(command.birthDate());
        }
    }

    public String surveyQuestions() throws IOException {
        ClassPathResource resource = new ClassPathResource("survey/survey_v1.json");
        String json = new String(resource.getInputStream().readAllBytes());

        return json;
    }

    @Transactional
    public OnboardingSurveyAnswerResult surveyAnswer(Long anonSessionId, OnboardingSurveyAnswerCommand command) {
        if (command.answer() == null || command.answer().qId() == null || command.answer().optionName() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "설문 답변(qId, value)이 올바르지 않습니다");
        }

        Integer qId = command.answer().qId();
        SurveyOptionName answerValue = command.answer().optionName();

        SurveyQuestion question = surveyLoader.getQuestion(qId);

        if (question == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 질문입니다: " + qId);
        }

        surveyAnswerRepository.save(SurveyAnswer.create(anonSessionId, qId, answerValue));

        if (qId.equals(surveyLoader.lastKey())) { saveAllSurveyAnswer(anonSessionId); }

        return new OnboardingSurveyAnswerResult(qId.equals(surveyLoader.lastKey()));
    }

    @Transactional
    public void saveAllSurveyAnswer(Long anonSessionId) {
        List<SurveyAnswer> answers = surveyAnswerRepository.findByAnonSessionId(anonSessionId);

        for (SurveyAnswer answer : answers) {
            SurveyQuestion question = surveyLoader.getQuestion(answer.getQId());
            PersonaElement personaElement = PersonaElement.create(answer.getAnonSessionId(), question.dimension(), question.traitFor(answer.getOptionName()));
            personaElementRepository.save(personaElement);
        }
    }
}
