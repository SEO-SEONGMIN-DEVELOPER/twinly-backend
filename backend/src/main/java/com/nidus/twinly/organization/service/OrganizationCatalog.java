package com.nidus.twinly.organization.service;

import com.nidus.twinly.app.service.AppFeatureFlagService;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.organization.domain.EmailOrganization;
import com.nidus.twinly.organization.domain.OrganizationType;
import com.nidus.twinly.organization.entity.Organization;
import com.nidus.twinly.organization.repository.OrganizationDomainRepository;
import com.nidus.twinly.organization.repository.OrganizationRepository;
import com.nidus.twinly.organization.writer.CompanyWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class OrganizationCatalog {

    private static final int DOMAIN_MAX_LENGTH = 255;
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("^[a-z0-9-]+(\\.[a-z0-9-]+)+$");

    private final OrganizationDomainRepository organizationDomainRepository;
    private final OrganizationRepository organizationRepository;
    private final BlockedEmailDomains blockedEmailDomains;
    private final CompanyWriter companyWriter;
    private final AppFeatureFlagService appFeatureFlagService;

    public EmailOrganization resolveEmail(String email) {
        String domain = extractDomain(email);

        EmailOrganization emailOrganization = organizationDomainRepository.findOrganizationByDomain(domain)
                .map(organization -> EmailOrganization.registered(domain, organization))
                .orElseGet(() -> unregisteredCompany(domain));

        if (emailOrganization.type() == OrganizationType.COMPANY && !appFeatureFlagService.companySignupEnabled()) {
            throw new BusinessException(ErrorCode.EMAIL_DOMAIN_NOT_SUPPORTED, "직장인 가입이 꺼져 있어 받을 수 없는 이메일 도메인입니다: " + domain);
        }

        return emailOrganization;
    }

    public Organization findSchoolByName(String name) {
        return organizationRepository.findByTypeAndName(OrganizationType.SCHOOL, name)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORGANIZATION_NOT_FOUND, "학교 목록에 없는 학교입니다: " + name));
    }

    public void requireSupportedDomain(String email) {
        resolveEmail(email);
    }

    public Organization registerCompany(String domain, String name) {
        return companyWriter.register(domain, name);
    }

    private EmailOrganization unregisteredCompany(String domain) {
        if (domain.length() > DOMAIN_MAX_LENGTH || !DOMAIN_PATTERN.matcher(domain).matches() || blockedEmailDomains.isBlocked(domain)) {
            throw new BusinessException(ErrorCode.EMAIL_DOMAIN_NOT_SUPPORTED, "가입할 수 없는 이메일 도메인입니다: " + domain);
        }

        return EmailOrganization.unregisteredCompany(domain);
    }

    private String extractDomain(String email) {
        int separatorIndex = email.lastIndexOf('@');

        if (separatorIndex < 0 || separatorIndex == email.length() - 1) {
            throw new BusinessException(ErrorCode.EMAIL_DOMAIN_NOT_SUPPORTED);
        }

        return email.substring(separatorIndex + 1).toLowerCase(Locale.ROOT);
    }
}
