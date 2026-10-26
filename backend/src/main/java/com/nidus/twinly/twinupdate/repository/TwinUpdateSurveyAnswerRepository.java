package com.nidus.twinly.twinupdate.repository;

import com.nidus.twinly.twinupdate.entity.TwinUpdateSurveyAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TwinUpdateSurveyAnswerRepository extends JpaRepository<TwinUpdateSurveyAnswer, Long> {

    List<TwinUpdateSurveyAnswer> findAllByTwinUpdateId(Long twinUpdateId);

    @Modifying
    @Query(value = """
            INSERT INTO twin_update_survey_answers (twin_update_id, question_id, option_name, created_at)
            VALUES (:twinUpdateId, :questionId, :optionName, UTC_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE option_name = :optionName
            """, nativeQuery = true)
    void upsert(@Param("twinUpdateId") Long twinUpdateId,
                @Param("questionId") Integer questionId,
                @Param("optionName") String optionName);

    @Modifying
    @Query("DELETE FROM TwinUpdateSurveyAnswer a WHERE a.twinUpdateId = :twinUpdateId")
    void deleteAllByTwinUpdateId(@Param("twinUpdateId") Long twinUpdateId);
}
