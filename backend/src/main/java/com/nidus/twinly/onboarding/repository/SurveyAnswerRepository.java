package com.nidus.twinly.onboarding.repository;

import com.nidus.twinly.onboarding.entity.SurveyAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SurveyAnswerRepository extends JpaRepository<SurveyAnswer, Long> {

    List<SurveyAnswer> findByAnonSessionId(Long anonSessionId);

    Optional<SurveyAnswer> findByAnonSessionIdAndQId(Long anonSessionId, Integer qId);
}
