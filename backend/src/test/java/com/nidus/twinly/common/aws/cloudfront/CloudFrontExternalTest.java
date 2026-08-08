package com.nidus.twinly.common.aws.cloudfront;

import com.nidus.twinly.common.aws.AwsConfig;
import com.nidus.twinly.common.aws.bedrock.BedrockProperties;
import com.nidus.twinly.common.aws.s3.S3Properties;
import com.nidus.twinly.common.aws.s3.S3Service;
import com.nidus.twinly.common.aws.ses.SesProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("external")
@SpringBootTest(classes = {AwsConfig.class, CloudFrontService.class, S3Service.class})
@EnableConfigurationProperties({
        CloudFrontProperties.class,
        S3Properties.class,
        BedrockProperties.class,
        SesProperties.class
})
class CloudFrontExternalTest {

    // 운영 자격증명에 s3:DeleteObject 권한이 없다(최소 권한).
    // 이 접두사에 걸린 버킷 라이프사이클 규칙이 만료 삭제를 대신한다.
    private static final String KEY_PREFIX = "external-test/";

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Autowired
    CloudFrontService cloudFrontService;

    @Autowired
    S3Service s3Service;

    private final String key = KEY_PREFIX + UUID.randomUUID() + ".txt";
    private final byte[] content = "cdn-probe".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void uploadOrigin() {
        s3Service.upload(key, content, "text/plain");
    }

    @Test
    @DisplayName("서명된 URL로 요청하면 CDN이 원본 객체를 그대로 내려준다")
    void signed_url_returns_object() throws IOException, InterruptedException {
        // given: 방금 올린 객체에 대한 서명 URL을 발급받는다
        String signedUrl = cloudFrontService.getSignedUrl(key);

        // when: 실제로 CDN에 HTTP 요청을 보낸다
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create(signedUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // then: 배포·키페어 등록·개인키 일치·원본 접근이 모두 성립해 본문이 그대로 온다
        System.out.println("\n===== 서명 URL =====\n" + signedUrl);
        System.out.println("===== 응답 =====\nstatus=" + response.statusCode() + ", body=" + response.body() + "\n");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(new String(content, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("서명 없는 URL로 요청하면 CDN이 거부한다")
    void unsigned_url_is_rejected() throws IOException, InterruptedException {
        // given: 같은 객체의 서명 없는 공개 URL
        String publicUrl = cloudFrontService.getPublicUrl(key);

        // when: 서명 없이 요청한다
        HttpResponse<Void> response = HTTP.send(
                HttpRequest.newBuilder(URI.create(publicUrl)).GET().build(),
                HttpResponse.BodyHandlers.discarding());

        // then: 서명 강제가 실제로 켜져 있어야 한다 (200이면 미디어가 공개 노출된 상태다)
        System.out.println("===== 서명 없는 요청 =====\n" + publicUrl + " → status=" + response.statusCode() + "\n");

        assertThat(response.statusCode()).isEqualTo(403);
    }
}
