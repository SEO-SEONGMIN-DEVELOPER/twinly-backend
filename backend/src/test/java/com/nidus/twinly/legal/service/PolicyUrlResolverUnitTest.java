package com.nidus.twinly.legal.service;

import com.nidus.twinly.legal.config.LegalProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyUrlResolverUnitTest {

    @Test
    @DisplayName("웹 도메인 뒤에 /legal/{정책 식별자}/ 를 붙인 약관 페이지 주소를 만든다")
    void resolve_builds_web_page_url_from_identifier() {
        // given: 웹 도메인이 설정된 상태
        PolicyUrlResolver resolver = new PolicyUrlResolver(new LegalProperties("https://trytwinly.com"));

        // when: 정책 식별자로 주소 생성
        String url = resolver.resolve("serviceTerms");

        // then: 웹에 배포된 약관 페이지 주소가 된다
        assertThat(url).isEqualTo("https://trytwinly.com/legal/serviceTerms/");
    }

    @Test
    @DisplayName("웹 도메인이 /로 끝나도 슬래시가 겹치지 않는 같은 주소를 만든다")
    void resolve_ignores_trailing_slash_of_base_url() {
        // given: 끝에 /가 붙은 웹 도메인
        PolicyUrlResolver resolver = new PolicyUrlResolver(new LegalProperties("https://trytwinly.com/"));

        // when: 정책 식별자로 주소 생성
        String url = resolver.resolve("serviceTerms");

        // then: /가 없을 때와 같은 주소가 된다
        assertThat(url).isEqualTo("https://trytwinly.com/legal/serviceTerms/");
    }
}
