package com.nidus.twinly.organization.writer;

import com.nidus.twinly.organization.entity.Organization;
import com.nidus.twinly.organization.event.CompanyRegisteredEvent;
import com.nidus.twinly.organization.repository.OrganizationDomainRepository;
import com.nidus.twinly.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CompanyWriter {

    private final OrganizationRepository organizationRepository;
    private final OrganizationDomainRepository organizationDomainRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Organization register(String domain, String name) {
        Organization organization = organizationRepository.save(Organization.createCompany(name));

        if (organizationDomainRepository.insertIgnore(organization.getId(), domain) == 0) {
            organizationRepository.delete(organization);
            return organizationDomainRepository.findOrganizationByDomainForShare(domain)
                    .orElseThrow(() -> new IllegalStateException("회사 도메인 등록이 충돌했지만 등록된 회사를 찾지 못했습니다: " + domain));
        }

        eventPublisher.publishEvent(new CompanyRegisteredEvent(organization.getId(), name, domain));

        return organization;
    }
}
