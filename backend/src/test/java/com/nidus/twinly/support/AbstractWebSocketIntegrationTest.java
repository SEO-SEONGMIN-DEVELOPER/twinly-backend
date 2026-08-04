package com.nidus.twinly.support;

import com.nidus.twinly.common.aws.bedrock.BedrockService;
import com.nidus.twinly.common.aws.s3.S3Service;
import com.nidus.twinly.common.aws.ses.SesService;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.solapi.SolapiService;
import com.nidus.twinly.common.websocket.dto.WebSocketResponseBody;
import com.nidus.twinly.connection.domain.ConnectionType;
import com.nidus.twinly.connection.dto.command.ConnectionTokenCommand;
import com.nidus.twinly.connection.service.ConnectionService;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Type;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket(STOMP) 통합 테스트 공통 베이스.
 *
 * <p>REST 통합 테스트({@link AbstractIntegrationTest})와의 결정적 차이:
 * <ul>
 *   <li><b>@Transactional 금지</b>: 서버의 메시지 처리는 별도 스레드(clientInboundChannel)에서 각자의
 *       트랜잭션으로 일어난다. 테스트 스레드의 트랜잭션 롤백은 그 스레드를 감싸지 못하고, 반대로 픽스처를
 *       미커밋 상태로 두면 핸들러 스레드가 아예 못 본다. 따라서 픽스처는 <b>실제 커밋</b>하고 정리는 수동으로 한다.</li>
 *   <li><b>실제 소켓 필요</b>: MockMvc(in-process)로는 검증 불가. WebSocketStompClient로 실제로 연결한다.</li>
 *   <li><b>전용 싱글턴 컨테이너</b>: 별도 컨테이너라 DB가 완전히 격리 → 하위 테스트는 @AfterEach에서
 *       deleteAll로 안전하게 정리하면 된다.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
public abstract class AbstractWebSocketIntegrationTest {

    @ServiceConnection
    protected static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_as_cs");

    @ServiceConnection
    protected static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4").withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    private static final long CONNECT_TIMEOUT_SECONDS = 5L;
    private static final long SUBSCRIBE_SETTLE_MS = 300L;

    @LocalServerPort
    protected int port;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected ConnectionService connectionService;

    // 서버와 동일한 JsonMapper를 클라이언트 변환기에도 써서 enum @JsonProperty 매핑을 일치시킨다.
    @Autowired
    protected JsonMapper jsonMapper;

    // 실제 외부 호출 차단 (컨텍스트 기동 + 부작용 방지)
    @MockitoBean protected SesService sesService;
    @MockitoBean protected SolapiService solapiService;
    @MockitoBean protected S3Service s3Service;
    @MockitoBean protected BedrockService bedrockService;

    private final AtomicInteger seq = new AtomicInteger();

    // receipt 추적·하트비트에 필요. WebSocketStompClient는 TaskScheduler가 없으면 receipt를 못 쓴다.
    private final ThreadPoolTaskScheduler taskScheduler = createTaskScheduler();

    private static ThreadPoolTaskScheduler createTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-test-");
        scheduler.initialize();
        return scheduler;
    }

    /** 실제 User를 DB에 저장하고 반환 (더미 값). */
    protected User saveUser() {
        int n = seq.incrementAndGet();
        return userRepository.save(User.create(
                "nick" + n,
                "family" + n, "familyHash" + n,
                "given" + n, "givenHash" + n,
                Gender.MALE,
                "aff" + n, "affHash" + n,
                "affNo" + n, "affNoHash" + n,
                "2000-01-01", "birthHash" + n,
                "phone" + n, "phoneHash" + n,
                "email" + n + "@test.com", "emailHash" + n
        ));
    }

    /** 해당 유저의 WS 접속 티켓(단발성)을 발급한다. 핸드셰이크 쿼리파라미터 ticket 로 쓴다. */
    protected UUID issueWsTicket(Long userId) {
        return connectionService.token(userId, new ConnectionTokenCommand(ConnectionType.WS)).ticket();
    }

    /** 티켓으로 실제 STOMP 연결을 맺어 세션을 돌려준다. 자동 receipt를 켜 구독 확정을 기다릴 수 있게 한다. */
    protected StompSession connect(UUID ticket) throws Exception {
        return connect(port, ticket);
    }

    /** 지정한 포트의 인스턴스로 STOMP 연결을 맺는다. 멀티 인스턴스 검증에서 두 번째 인스턴스에 붙을 때 쓴다. */
    protected StompSession connect(int serverPort, UUID ticket) throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter(jsonMapper));
        stompClient.setTaskScheduler(taskScheduler);

        String url = "ws://localhost:" + serverPort + "/ws/v1/?ticket=" + ticket;
        StompSession session = stompClient
                .connectAsync(url, new StompSessionHandlerAdapter() {})
                .get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        session.setAutoReceipt(true);
        return session;
    }

    /**
     * destination을 구독하고, 서버가 보내는 프레임을 담을 큐를 돌려준다.
     *
     * <p>SimpleBroker는 SUBSCRIBE에 RECEIPT를 돌려주지 않아 구독 확정을 확인할 방법이 없다.
     * 게다가 clientInboundChannel은 멀티스레드라 SUBSCRIBE와 뒤이은 SEND의 처리 순서가 보장되지 않는다.
     * 그래서 짧은 settle 지연으로 구독이 먼저 등록되도록 한 뒤 반환한다(전송된 응답 프레임 유실 방지).
     */
    protected <T extends WebSocketResponseBody> BlockingQueue<T> subscribe(
            StompSession session, String destination, Class<T> bodyType) throws InterruptedException {
        BlockingQueue<T> received = new LinkedBlockingQueue<>();

        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return bodyType;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add(bodyType.cast(payload));
            }
        });

        Thread.sleep(SUBSCRIBE_SETTLE_MS);
        return received;
    }
}
