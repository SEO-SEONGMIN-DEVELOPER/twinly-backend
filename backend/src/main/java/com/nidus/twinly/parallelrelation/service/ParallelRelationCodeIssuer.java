package com.nidus.twinly.parallelrelation.service;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.parallelrelation.repository.ParallelRelationCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
@RequiredArgsConstructor
public class ParallelRelationCodeIssuer {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final int ISSUE_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ParallelRelationCodeRepository parallelRelationCodeRepository;

    public String issue() {
        for (int attempt = 0; attempt < ISSUE_ATTEMPTS; attempt++) {
            String code = randomCode();

            if (!parallelRelationCodeRepository.existsByCode(code)) {
                return code;
            }
        }

        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "코드 발급에 실패했습니다.");
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);

        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }

        return code.toString();
    }
}
