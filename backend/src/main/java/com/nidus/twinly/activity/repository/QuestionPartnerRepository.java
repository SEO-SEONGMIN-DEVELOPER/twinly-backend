package com.nidus.twinly.activity.repository;

import com.nidus.twinly.activity.entity.QuestionPartner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionPartnerRepository extends JpaRepository<QuestionPartner, Long> {

    List<QuestionPartner> findAllByQuestionIdIn(List<Long> questionIds);
}