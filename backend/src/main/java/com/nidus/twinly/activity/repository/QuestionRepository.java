package com.nidus.twinly.activity.repository;

import com.nidus.twinly.activity.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findAllByUserIdAndDate(Long userId, LocalDate date);
}