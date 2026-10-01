package com.nidus.twinly.user.repository;

import com.nidus.twinly.user.entity.UserFeedbackOption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFeedbackOptionRepository extends JpaRepository<UserFeedbackOption, Long> {
}
