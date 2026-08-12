package com.nidus.twinly.user.service;

import com.nidus.twinly.common.jwt.JwtService;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.user.dto.header.UserInfo;
import com.nidus.twinly.user.dto.result.UsersPageResult;
import com.nidus.twinly.user.dto.result.UsersResult;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int DEFAULT_LIMIT = 500;

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

    @Transactional(readOnly = true)
    public UsersResult users(Long cursor, Integer limit) {
        int effectiveLimit = (limit != null && limit > 0) ? limit : DEFAULT_LIMIT;

        List<Long> fetched = userRepository.findIdsAfterCursor(cursor, effectiveLimit + 1);

        boolean hasMore = fetched.size() > effectiveLimit;
        List<Long> userIds = hasMore ? fetched.subList(0, effectiveLimit) : fetched;

        Long nextCursor = hasMore ? userIds.get(userIds.size() - 1) : null;

        return new UsersResult(userIds, new UsersPageResult(nextCursor, hasMore));
    }
}
