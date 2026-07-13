package com.nidus.twinly.user.repository;

import com.nidus.twinly.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByNickname(String nickname);

    boolean existsByPhoneNumberHash(String phoneNumberHash);

    boolean existsByEmailHash(String emailHash);

    Optional<User> findByPhoneNumberHash(String phoneNumberHash);
}