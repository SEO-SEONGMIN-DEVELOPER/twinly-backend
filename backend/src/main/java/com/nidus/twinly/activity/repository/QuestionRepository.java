package com.nidus.twinly.activity.repository;

import com.nidus.twinly.activity.domain.QuestionType;
import com.nidus.twinly.activity.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findAllByUserIdAndDate(Long userId, LocalDate date);

    List<Question> findAllByUserIdAndTypeAndIsSkippedFalse(Long userId, QuestionType type);

    List<Question> findAllByUserIdAndTypeAndIsSkippedFalseAndDate(Long userId, QuestionType type, LocalDate date);

    @Modifying
    @Query("DELETE FROM Question q WHERE q.userId IN :userIds")
    void deleteAllByUserIdIn(@Param("userIds") List<Long> userIds);
}
