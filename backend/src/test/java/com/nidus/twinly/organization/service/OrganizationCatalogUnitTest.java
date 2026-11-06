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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OrganizationCatalogUnitTest {

    @Mock
    OrganizationDomainRepository organizationDomainRepository;

    @Mock
    OrganizationRepository organizationRepository;

    @Mock
    BlockedEmailDomains blockedEmailDomains;

    @Mock
    CompanyWriter companyWriter;

    @Mock
    AppFeatureFlagService appFeatureFlagService;

    @InjectMocks
    OrganizationCatalog organizationCatalog;

    @Test
    @DisplayName("등록된 도메인이면 그 기관과 기관의 종류를 돌려주고 차단 목록은 보지 않는다")
    void resolveEmail_registered_domain_returns_organization() {
        // given: 도메인이 대문자로 섞여 와도 소문자로 등록된 학교를 찾는다
        Organization school = organization(1L, "트윈리대학교", OrganizationType.SCHOOL);
        given(organizationDomainRepository.findOrganizationByDomain("nidus.ac.kr")).willReturn(Optional.of(school));

        // when: 이메일 판정
        EmailOrganization result = organizationCatalog.resolveEmail("student@NIDUS.ac.kr");

        // then: 학교로 판정되고 차단 목록·직장인 가입 스위치는 확인하지 않는다
        assertThat(result).isEqualTo(new EmailOrganization("nidus.ac.kr", OrganizationType.SCHOOL, school));
        then(blockedEmailDomains).should(never()).isBlocked(anyString());
        then(appFeatureFlagService).should(never()).companySignupEnabled();
    }

    @Test
    @DisplayName("등록되지 않았고 차단되지 않은 도메인이면 처음 보는 회사로 판정한다")
    void resolveEmail_unregistered_domain_is_new_company() {
        // given: 등록되지 않은 일반 회사 도메인
        given(organizationDomainRepository.findOrganizationByDomain("twinly-company.co.kr")).willReturn(Optional.empty());
        given(blockedEmailDomains.isBlocked("twinly-company.co.kr")).willReturn(false);
        given(appFeatureFlagService.companySignupEnabled()).willReturn(true);

        // when: 이메일 판정
        EmailOrganization result = organizationCatalog.resolveEmail("worker@twinly-company.co.kr");

        // then: 기관 없이 회사 종류로 판정된다
        assertThat(result).isEqualTo(EmailOrganization.unregisteredCompany("twinly-company.co.kr"));
        assertThat(result.isUnregisteredCompany()).isTrue();
    }

    @Test
    @DisplayName("직장인 가입 스위치가 꺼져 있으면 처음 보는 회사 도메인을 EMAIL_DOMAIN_NOT_SUPPORTED 로 거절한다")
    void resolveEmail_new_company_while_switch_off_throws() {
        // given: 차단되지 않은 회사 도메인이지만 직장인 가입이 꺼진 상태
        given(organizationDomainRepository.findOrganizationByDomain("twinly-company.co.kr")).willReturn(Optional.empty());
        given(blockedEmailDomains.isBlocked("twinly-company.co.kr")).willReturn(false);
        given(appFeatureFlagService.companySignupEnabled()).willReturn(false);

        // when & then: 예전 대학생 전용 앱과 똑같이 거절한다
        assertThatThrownBy(() -> organizationCatalog.resolveEmail("worker@twinly-company.co.kr"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_DOMAIN_NOT_SUPPORTED);
    }

    @Test
    @DisplayName("직장인 가입 스위치가 꺼져 있으면 이미 등록된 회사 도메인도 거절한다")
    void resolveEmail_registered_company_while_switch_off_throws() {
        // given: 이미 등록된 회사가 있지만 직장인 가입이 꺼진 상태
        given(organizationDomainRepository.findOrganizationByDomain("twinly-company.co.kr"))
                .willReturn(Optional.of(organization(10L, "트윈리컴퍼니", OrganizationType.COMPANY)));
        given(appFeatureFlagService.companySignupEnabled()).willReturn(false);

        // when & then: 스위치를 끄면 새 직장인 가입이 전부 멈춘다
        assertThatThrownBy(() -> organizationCatalog.resolveEmail("worker@twinly-company.co.kr"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_DOMAIN_NOT_SUPPORTED);
    }

    @Test
    @DisplayName("등록되지 않은 도메인이 차단 목록에 있으면 EMAIL_DOMAIN_NOT_SUPPORTED 예외가 발생한다")
    void resolveEmail_blocked_domain_throws() {
        // given: 개인 메일 도메인
        given(organizationDomainRepository.findOrganizationByDomain("gmail.com")).willReturn(Optional.empty());
        given(blockedEmailDomains.isBlocked("gmail.com")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> organizationCatalog.resolveEmail("someone@gmail.com"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_DOMAIN_NOT_SUPPORTED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"worker@localhost", "worker@", "worker", "worker@two words.com", "worker@.com", "worker@company..com"})
    @DisplayName("도메인 모양이 아니면 처음 보는 회사로 받지 않고 EMAIL_DOMAIN_NOT_SUPPORTED 예외가 발생한다")
    void resolveEmail_malformed_domain_throws(String email) {
        // given: 어떤 도메인도 등록되어 있지 않음
        lenient().when(organizationDomainRepository.findOrganizationByDomain(anyString())).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> organizationCatalog.resolveEmail(email))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_DOMAIN_NOT_SUPPORTED);
    }

    @Test
    @DisplayName("학교 이름 조회는 학교 종류 안에서만 찾고, 없으면 ORGANIZATION_NOT_FOUND 예외가 발생한다")
    void findSchoolByName_searches_only_schools() {
        // given: 같은 이름의 학교가 없음 (같은 이름의 회사가 있더라도 대상이 아니다)
        given(organizationRepository.findByTypeAndName(OrganizationType.SCHOOL, "서울대학교")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> organizationCatalog.findSchoolByName("서울대학교"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORGANIZATION_NOT_FOUND);
    }

    @Test
    @DisplayName("회사 등록은 도메인과 회사명을 그대로 CompanyWriter 에 맡기고 결과를 돌려준다")
    void registerCompany_delegates_to_writer() {
        // given: writer 가 등록(또는 먼저 등록된 회사)을 돌려줌
        Organization company = organization(10L, "트윈리컴퍼니", OrganizationType.COMPANY);
        given(companyWriter.register("twinly-company.com", "트윈리컴퍼니")).willReturn(company);

        // when & then
        assertThat(organizationCatalog.registerCompany("twinly-company.com", "트윈리컴퍼니")).isSameAs(company);
    }

    @Test
    @DisplayName("도메인이 255자를 넘으면 처음 보는 회사로 받지 않는다")
    void resolveEmail_too_long_domain_throws() {
        // given: 등록되지 않은 아주 긴 도메인
        String domain = "a".repeat(250) + ".co.kr";
        given(organizationDomainRepository.findOrganizationByDomain(domain)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> organizationCatalog.resolveEmail("worker@" + domain))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_DOMAIN_NOT_SUPPORTED);
    }

    private Organization organization(Long id, String name, OrganizationType type) {
        Organization organization = BeanUtils.instantiateClass(Organization.class);
        ReflectionTestUtils.setField(organization, "id", id);
        ReflectionTestUtils.setField(organization, "name", name);
        ReflectionTestUtils.setField(organization, "type", type);
        return organization;
    }
}
