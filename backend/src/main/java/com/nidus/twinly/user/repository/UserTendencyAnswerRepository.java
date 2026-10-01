package com.nidus.twinly.user.repository;

import com.nidus.twinly.user.entity.UserTendencyAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTendencyAnswerRepository extends JpaRepository<UserTendencyAnswer, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO user_tendency_answers (user_id, question_id, option_id, created_at)
            VALUES (:userId, :questionId, :optionId, UTC_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE option_id = :optionId
            """, nativeQuery = true)
    void upsert(@Param("userId") Long userId,
                @Param("questionId") Long questionId,
                @Param("optionId") Long optionId);
}
