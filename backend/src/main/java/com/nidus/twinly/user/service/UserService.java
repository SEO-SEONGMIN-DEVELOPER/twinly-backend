package com.nidus.twinly.user.service;

import com.nidus.twinly.common.jwt.JwtService;
import com.nidus.twinly.user.dto.header.UserInfo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserService {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public UserInfo resolveByAccessToken(String token) {
        Long userId;
        try {
            userId = jwtService.parseAccessTokenUserId(token);
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다", e);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "존재하지 않는 유저입니다"));

        if (user.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "탈퇴한 유저입니다");
        }

        return new UserInfo(user.getId());
    }
}
