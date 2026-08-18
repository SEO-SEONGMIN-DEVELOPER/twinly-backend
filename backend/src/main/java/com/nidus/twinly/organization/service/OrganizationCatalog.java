package com.nidus.twinly.organization.service;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.organization.entity.Organization;
import com.nidus.twinly.organization.repository.OrganizationDomainRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class OrganizationCatalog {

    private final OrganizationDomainRepository organizationDomainRepository;

    public Organization findByEmail(String email) {
        String domain = extractDomain(email);

        return organizationDomainRepository.findOrganizationByDomain(domain)
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
