package com.nidus.twinly.organization.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class BlockedEmailDomainsUnitTest {

    BlockedEmailDomains blockedEmailDomains;

    @BeforeEach
    void setUp() throws IOException {
        blockedEmailDomains = new BlockedEmailDomains();
        blockedEmailDomains.load();
    }

    @ParameterizedTest
    @ValueSource(strings = {"naver.com", "hanmail.net", "gmail.com", "icloud.com", "mailinator.com", "yopmail.com"})
    @DisplayName("국내·해외 개인 메일과 일회용 메일 도메인은 차단한다")
    void isBlocked_personal_and_disposable_domains(String domain) {
        // when & then: 리소스 파일 두 개에서 읽은 도메인은 모두 차단된다
        assertThat(blockedEmailDomains.isBlocked(domain)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"unregistered.ac.kr", "grad.unregistered.ac.kr", "college.edu", "ox.ac.uk", "u-tokyo.ac.jp", "pku.edu.cn"})
    @DisplayName("목록에 없는 대학 도메인은 학생이 직장인 풀에 섞이지 않도록 회사로 받지 않는다")
    void isBlocked_higher_education_domains(String domain) {
        // when & then
        assertThat(blockedEmailDomains.isBlocked(domain)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"samsung.com", "twinly-company.co.kr", "seoul.go.kr", "teacher.hs.kr", "academy.com"})
    @DisplayName("회사·공공기관·초중고 교직원 도메인은 차단하지 않는다")
    void isBlocked_allows_company_domains(String domain) {
        // when & then
        assertThat(blockedEmailDomains.isBlocked(domain)).isFalse();
    }
}
