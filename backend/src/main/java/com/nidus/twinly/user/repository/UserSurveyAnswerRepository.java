package com.nidus.twinly.user.repository;

import com.nidus.twinly.user.entity.UserSurveyAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserSurveyAnswerRepository extends JpaRepository<UserSurveyAnswer, Long> {

    List<UserSurveyAnswer> findAllByUserId(Long userId);

    boolean existsByUserId(Long userId);

    @Modifying
    @Query(value = """
            INSERT INTO user_survey_answers (user_id, question_id, option_name, created_at)
            VALUES (:userId, :questionId, :optionName, UTC_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE option_name = :optionName
            """, nativeQuery = true)
    void upsert(@Param("userId") Long userId,
                @Param("questionId") Integer questionId,
                @Param("optionName") String optionName);
}
