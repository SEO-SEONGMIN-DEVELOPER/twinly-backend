package com.nidus.twinly.common.web;

import com.nidus.twinly.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

// MockMvc 는 Tomcat 을 거치지 않아 압축이 일어나지 않으므로, 띄운 포트로 실제 HTTP 요청을 보낸다.
class ResponseCompressionIntegrationTest extends AbstractIntegrationTest {

    private static final String LARGE_PUBLIC_PATH = "/api/v1/onboarding/organizations";

    @LocalServerPort
    int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    @DisplayName("Accept-Encoding: gzip 으로 JSON 을 부르면 gzip 으로 압축해 주고, 풀면 원래 JSON 이다")
    void compresses_large_json_when_client_accepts_gzip() throws Exception {
        // when: Accept-Encoding: gzip 으로 공개 학교 목록 조회
        HttpResponse<byte[]> response = get(LARGE_PUBLIC_PATH, true);

        // then: 200 + gzip 압축 + Vary 헤더, 풀면 2KB 넘는 JSON
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Encoding")).hasValue("gzip");
        assertThat(response.headers().allValues("Vary")).anyMatch(vary -> vary.equalsIgnoreCase("Accept-Encoding"));
        String json = gunzip(response.body());
        assertThat(json.length()).isGreaterThan(2048);
        assertThat(json).startsWith("{");
    }

    @Test
    @DisplayName("Accept-Encoding 을 보내지 않으면 압축하지 않는다")
    void does_not_compress_without_accept_encoding() throws Exception {
        // when: Accept-Encoding 없이 같은 목록 조회
        HttpResponse<byte[]> response = get(LARGE_PUBLIC_PATH, false);

        // then: 압축하지 않은 JSON 그대로
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Encoding")).isEmpty();
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).startsWith("{");
    }

    private HttpResponse<byte[]> get(String path, boolean acceptGzip) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (acceptGzip) {
            request.header("Accept-Encoding", "gzip");
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static String gunzip(byte[] body) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(body))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
