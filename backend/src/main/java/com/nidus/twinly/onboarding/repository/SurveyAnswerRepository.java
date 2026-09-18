package com.nidus.twinly.onboarding.repository;

import com.nidus.twinly.onboarding.entity.SurveyAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SurveyAnswerRepository extends JpaRepository<SurveyAnswer, Long> {

    List<SurveyAnswer> findAllByAnonSessionId(Long anonSessionId);

    @Modifying
    @Query(value = """
            INSERT INTO survey_answers (anon_session_id, question_id, option_name, created_at)
            VALUES (:anonSessionId, :questionId, :optionName, UTC_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE option_name = :optionName
            """, nativeQuery = true)
    void upsert(@Param("anonSessionId") Long anonSessionId,
                @Param("questionId") Integer questionId,
                @Param("optionName") String optionName);

    void deleteByAnonSessionId(Long anonSessionId);
}
