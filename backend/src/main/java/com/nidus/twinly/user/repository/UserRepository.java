package com.nidus.twinly.user.repository;

import com.nidus.twinly.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByNickname(String nickname);

    boolean existsByPhoneNumberHash(String phoneNumberHash);

    boolean existsByEmailHash(String emailHash);

    Optional<User> findByPhoneNumberHash(String phoneNumberHash);

    List<User> findAllByDeletedAtIsNullAndWithdrawalScheduledAtLessThanEqual(Instant now, Pageable pageable);
}