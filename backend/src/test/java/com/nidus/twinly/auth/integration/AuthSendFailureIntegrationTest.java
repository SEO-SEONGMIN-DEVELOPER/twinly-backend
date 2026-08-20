package com.nidus.twinly.auth.integration;

import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.auth.repository.AnonSessionVerificationSessionRepository;
import com.nidus.twinly.common.domain.VerificationType;
import com.nidus.twinly.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증번호 발송이 외부 사유로 실패했을 때의 경계를 고정한다.
 *
 * <p>인증 세션을 먼저 저장하고 발송을 뒤에 하므로, 발송이 터지면 세션 저장도 함께 롤백되어야 한다.
 * 코드가 전달되지 않았는데 세션만 남으면 사용자는 받은 적 없는 코드를 요구받는다.
 *
 * <p>이 롤백은 테스트 트랜잭션 안에서는 검증할 수 없다. 서비스가 테스트 트랜잭션에 합류해 버려
 * 실제 커밋/롤백이 일어나지 않고, 영속성 컨텍스트에 남은 엔티티가 조회에 그대로 잡히기 때문이다.
 * 그래서 테스트 트랜잭션을 끄고 실제 롤백을 관찰한다.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthSendFailureIntegrationTest extends AbstractIntegrationTest {

    private static final String ORGANIZATION = "발송실패대학교";
    private static final String DOMAIN = "sendfail.ac.kr";

    @Autowired
    AnonSessionRepository anonSessionRepository;

    @Autowired
    AnonSessionVerificationSessionRepository anonSessionVerificationSessionRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        // 롤백이 없으므로 직접 지운다. 순서는 FK 의존의 역방향(자식 → 부모)
        anonSessionVerificationSessionRepository.deleteAll();
        anonSessionRepository.deleteAll();
        // 시드가 채워둔 조직·도메인을 지우지 않도록 이 테스트가 만든 것만 좁혀서 지운다
        jdbcTemplate.update("DELETE FROM organization_domains WHERE domain = ?", DOMAIN);
        jdbcTemplate.update("DELETE FROM organizations WHERE name = ?", ORGANIZATION);
    }

    @Test
    @DisplayName("이메일 발송이 실패하면 500을 반환하고 인증 세션도 남기지 않는다")
    void email_send_failure_rolls_back_verification_session() throws Exception {
        // given: 가입 가능한 도메인이지만 SES가 장애인 상황
        AnonSession anonSession = saveAnonSession();
        saveOrganization(ORGANIZATION, DOMAIN);
        willThrow(new IllegalStateException("SES 장애"))
                .given(sesService).send(anyString(), anyString(), anyString());

        // when: 온보딩 이메일 인증번호 발송
        mockMvc.perform(post("/api/v1/auth/onboarding/email/send")
                        .header("Authorization", "Bearer " + anonSession.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"someone@sendfail.ac.kr"}
                                """))
                .andExpect(status().isInternalServerError());

        // then: 코드를 못 받았으므로 인증 세션도 남지 않는다 (실제 롤백을 DB에서 확인)
        assertThat(anonSessionVerificationSessionRepository
                .findByAnonSessionIdAndType(anonSession.getId(), VerificationType.EMAIL)).isEmpty();
    }

    @Test
    @DisplayName("SMS 발송이 실패하면 500을 반환하고 인증 세션도 남기지 않는다")
    void sms_send_failure_rolls_back_verification_session() throws Exception {
        // given: SOLAPI가 장애인 상황
        AnonSession anonSession = saveAnonSession();
        willThrow(new IllegalStateException("SOLAPI 장애"))
                .given(solapiService).send(anyString(), anyString());

        // when: 온보딩 SMS 인증번호 발송
        mockMvc.perform(post("/api/v1/auth/onboarding/sms/send")
                        .header("Authorization", "Bearer " + anonSession.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"01098765432"}
                                """))
                .andExpect(status().isInternalServerError());

        // then: 인증 세션이 남지 않는다
        assertThat(anonSessionVerificationSessionRepository
                .findByAnonSessionIdAndType(anonSession.getId(), VerificationType.SMS)).isEmpty();
    }

    private AnonSession saveAnonSession() {
        return anonSessionRepository.save(
                AnonSession.create(UUID.randomUUID(), Instant.now().plus(Duration.ofDays(1))));
    }

    private void saveOrganization(String name, String domain) {
        jdbcTemplate.update("INSERT INTO organizations (name) VALUES (?)", name);
        Long organizationId = jdbcTemplate.queryForObject(
                "SELECT id FROM organizations WHERE name = ?", Long.class, name);
        jdbcTemplate.update("INSERT INTO organization_domains (organization_id, domain) VALUES (?, ?)",
                organizationId, domain);
    }
}
