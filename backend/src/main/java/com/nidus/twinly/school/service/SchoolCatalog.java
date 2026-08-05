package com.nidus.twinly.school.service;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.school.entity.School;
import com.nidus.twinly.school.repository.SchoolDomainRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class SchoolCatalog {

    private final SchoolDomainRepository schoolDomainRepository;

    public School findByEmail(String email) {
        String domain = extractDomain(email);

        return schoolDomainRepository.findSchoolByDomain(domain)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_DOMAIN_NOT_SUPPORTED, "가입할 수 없는 이메일 도메인입니다: " + domain));
    }

    public void requireSupportedDomain(String email) {
        findByEmail(email);
    }

    private String extractDomain(String email) {
        int separatorIndex = email.lastIndexOf('@');

        if (separatorIndex < 0 || separatorIndex == email.length() - 1) {
            throw new BusinessException(ErrorCode.EMAIL_DOMAIN_NOT_SUPPORTED);
        }

        return email.substring(separatorIndex + 1).toLowerCase(Locale.ROOT);
    }
}
