package com.nidus.twinly.user.service;

import com.nidus.twinly.common.jwt.JwtService;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.user.dto.header.UserInfo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
            throw new BusinessException(ErrorCode.INVALID_TOKEN, e);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        if (user.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.WITHDRAWN_USER);
        }

        return new UserInfo(user.getId());
    }
}
