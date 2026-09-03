package com.nidus.twinly.activity.repository;

import com.nidus.twinly.activity.entity.QuestionPartner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestionPartnerRepository extends JpaRepository<QuestionPartner, Long> {

    List<QuestionPartner> findAllByQuestionIdIn(List<Long> questionIds);

    void deleteAllByQuestionIdIn(List<Long> questionIds);

    @Modifying
    @Query("DELETE FROM QuestionPartner qp WHERE qp.questionId IN (SELECT q.id FROM Question q WHERE q.userId IN :userIds)")
    void deleteAllByQuestionUserIdIn(@Param("userIds") List<Long> userIds);
}
