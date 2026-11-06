package com.nidus.twinly.organization.writer;

import com.nidus.twinly.organization.domain.OrganizationType;
import com.nidus.twinly.organization.entity.Organization;
import com.nidus.twinly.organization.event.CompanyRegisteredEvent;
import com.nidus.twinly.organization.repository.OrganizationDomainRepository;
import com.nidus.twinly.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CompanyWriterUnitTest {

    @Mock
    OrganizationRepository organizationRepository;

    @Mock
    OrganizationDomainRepository organizationDomainRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    CompanyWriter companyWriter;

    @Test
    @DisplayName("처음 등록되는 도메인이면 회사 종류로 저장하고 도메인을 묶은 뒤 등록 이벤트를 발행한다")
    void register_new_domain_saves_company_and_publishes_event() {
        // given: 저장하면 id 10 이 붙고, 도메인 삽입이 1건 성공
        given(organizationRepository.save(any(Organization.class))).willAnswer(invocation -> {
            Organization organization = invocation.getArgument(0);
            ReflectionTestUtils.setField(organization, "id", 10L);
            return organization;
        });
        given(organizationDomainRepository.insertIgnore(10L, "twinly-company.com")).willReturn(1);

        // when: 회사 등록
        Organization result = companyWriter.register("twinly-company.com", "트윈리컴퍼니");

        // then: 회사 종류·입력 이름으로 저장되고 Slack 알림용 이벤트가 나간다
        assertThat(result.getType()).isEqualTo(OrganizationType.COMPANY);
        assertThat(result.getName()).isEqualTo("트윈리컴퍼니");
        ArgumentCaptor<CompanyRegisteredEvent> captor = ArgumentCaptor.forClass(CompanyRegisteredEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new CompanyRegisteredEvent(10L, "트윈리컴퍼니", "twinly-company.com"));
        then(organizationRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("다른 가입자가 같은 도메인을 먼저 등록했으면 방금 만든 회사를 지우고 먼저 등록된 회사를 돌려주며 이벤트는 내지 않는다")
    void register_conflicting_domain_returns_existing_company() {
        // given: 도메인 삽입이 중복으로 무시되고, 잠금 읽기로 먼저 등록된 회사가 보임
        given(organizationRepository.save(any(Organization.class))).willAnswer(invocation -> {
            Organization organization = invocation.getArgument(0);
            ReflectionTestUtils.setField(organization, "id", 10L);
            return organization;
        });
        given(organizationDomainRepository.insertIgnore(10L, "twinly-company.com")).willReturn(0);
        Organization existing = company(7L, "먼저등록된회사");
        given(organizationDomainRepository.findOrganizationByDomainForShare("twinly-company.com")).willReturn(Optional.of(existing));

        // when: 회사 등록
        Organization result = companyWriter.register("twinly-company.com", "트윈리컴퍼니");

        // then: 이름 선점 없이 먼저 등록된 회사로 이어지고, 고아 회사 행은 남지 않는다
        assertThat(result).isSameAs(existing);
        then(organizationRepository).should().delete(any(Organization.class));
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("도메인 삽입이 무시됐는데 등록된 회사를 찾을 수 없으면 예외를 던진다")
    void register_conflict_without_existing_throws() {
        // given: 삽입이 무시됐지만 잠금 읽기에도 보이지 않음
        given(organizationRepository.save(any(Organization.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(organizationDomainRepository.insertIgnore(any(), any())).willReturn(0);
        given(organizationDomainRepository.findOrganizationByDomainForShare("twinly-company.com")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> companyWriter.register("twinly-company.com", "트윈리컴퍼니"))
                .isInstanceOf(IllegalStateException.class);
    }

    private Organization company(Long id, String name) {
        Organization organization = BeanUtils.instantiateClass(Organization.class);
        ReflectionTestUtils.setField(organization, "id", id);
        ReflectionTestUtils.setField(organization, "name", name);
        ReflectionTestUtils.setField(organization, "type", OrganizationType.COMPANY);
        return organization;
    }
}
