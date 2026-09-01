package com.nidus.twinly.support;

import com.google.firebase.messaging.FirebaseMessaging;
import com.nidus.twinly.common.aws.bedrock.BedrockService;
import com.nidus.twinly.common.aws.s3.S3Service;
import com.nidus.twinly.common.aws.ses.SesService;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.jwt.JwtService;
import com.nidus.twinly.common.solapi.SolapiService;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 통합 테스트 공통 베이스.
 * - RANDOM_PORT: 실제 서블릿 컨테이너 기동(WebSocketConfig 요구), MockMvc는 in-process 디스패치
 * - @Transactional: 각 테스트 후 롤백으로 DB 정리
 * - Testcontainers MySQL(@ServiceConnection): 격리된 실제 MySQL, Flyway가 스키마 적용
 *   - 콜레이션은 운영 RDS와 같은 ai_ci. cs로 두면 닉네임 중복 판정이 운영과 달라진다.
 *   - connectionTimeZone=UTC는 운영 URL의 serverTimezone=UTC와 같은 설정. 없으면 JVM 기본
 *     시간대를 쓰게 되어, SQL의 UTC_TIMESTAMP()와 섞이는 지점이 로컬(KST)에서만 9시간 어긋난다.
 * - 외부 어댑터 @MockitoBean: 실제 AWS/SOLAPI 호출 차단
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
@Tag("integration")   // 하위 통합 테스트가 상속 → gradle에서 test(단위)와 분리 실행
public abstract class AbstractIntegrationTest {

    // 싱글턴 컨테이너: 정적 초기화로 한 번만 start → 모든 통합 테스트 클래스가 공유한다.
    // (@Testcontainers는 클래스마다 stop하는데, Spring 컨텍스트 캐시가 재사용되면 죽은 컨테이너를 가리켜 실패)
    // Ryuk이 JVM 종료 시 컨테이너를 정리한다.
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci")
            .withUrlParam("connectionTimeZone", "UTC");

    @ServiceConnection
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4").withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected JwtService jwtService;

    // 실제 외부 호출 차단 (메시지 발송·S3·Bedrock)
    @MockitoBean protected SesService sesService;
    @MockitoBean protected SolapiService solapiService;
    @MockitoBean protected S3Service s3Service;
    @MockitoBean protected BedrockService bedrockService;
    @MockitoBean protected FirebaseMessaging firebaseMessaging;

    private final AtomicInteger seq = new AtomicInteger();

    /** 실제 User를 DB에 저장하고 반환 (더미 값). */
    protected User saveUser() {
        int n = seq.incrementAndGet();
        return userRepository.save(User.create(
                "nick" + n,
                "family" + n, "familyHash" + n,
                "given" + n, "givenHash" + n,
                Gender.MALE,
                "organization" + n, "organizationHash" + n,
                "aff" + n, "affHash" + n,
                "affNo" + n, "affNoHash" + n,
                "2000-01-01", "birthHash" + n,
                "phone" + n, "phoneHash" + n,
                "email" + n + "@test.com", "emailHash" + n, null, null
        ));
    }

    /** 해당 유저의 실제 액세스 토큰으로 Authorization 헤더 값을 만든다. */
    protected String bearer(Long userId) {
        return "Bearer " + jwtService.generateAccessToken(userId).value();
    }
}
