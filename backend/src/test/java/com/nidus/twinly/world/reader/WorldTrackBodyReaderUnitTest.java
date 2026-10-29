package com.nidus.twinly.world.reader;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.world.config.WorldTrackProperties;
import com.nidus.twinly.world.dto.request.WorldTrackRequest;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorldTrackBodyReaderUnitTest {

    private static final String LONG_EXTRA_REF = "extra:" + "1234567890".repeat(8);

    private final WorldTrackBodyReader reader = readerWithLimits(DataSize.ofKilobytes(64), DataSize.ofKilobytes(64));

    @Test
    @DisplayName("gzip 이 아닌 본문은 읽지 않고 UNSUPPORTED_MEDIA_TYPE 으로 거절하며 Accept-Encoding: gzip 을 알려 준다")
    void readCompressed_rejects_body_without_gzip_encoding() {
        // given: Content-Encoding 이 없거나 gzip 이 아닌 요청
        byte[] plain = track("null").getBytes(StandardCharsets.UTF_8);

        // when & then: Content-Encoding 없음·deflate 모두 415 코드와 Accept-Encoding: gzip 헤더로 거절
        for (String contentEncoding : new String[]{null, "deflate"}) {
            assertThatThrownBy(() -> reader.readCompressed(contentEncoding, new ByteArrayInputStream(plain)))
                    .isInstanceOfSatisfying(BusinessException.class, e -> {
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
                        assertThat(e.getHeaders().getFirst(HttpHeaders.ACCEPT_ENCODING)).isEqualTo("gzip");
                    });
        }
    }

    @Test
    @DisplayName("gzip 본문은 받은 바이트 그대로 돌려준다 (대소문자 구분 없음)")
    void readCompressed_returns_received_bytes_as_is() {
        // given: gzip 으로 압축한 정상 본문
        byte[] compressed = gzip(track("null"));

        // when: 대문자 GZIP 헤더로 압축 본문 읽기
        byte[] read = reader.readCompressed("GZIP", new ByteArrayInputStream(compressed));

        // then: 받은 바이트와 한 글자도 다르지 않다
        assertThat(read).isEqualTo(compressed);
    }

    @Test
    @DisplayName("압축 본문이 상한을 넘으면 WORLD_TRACK_TOO_LARGE 로 거절한다")
    void readCompressed_rejects_body_over_compressed_limit() {
        // given: 압축 상한 1KB, 그보다 큰 본문
        WorldTrackBodyReader small = readerWithLimits(DataSize.ofKilobytes(1), DataSize.ofKilobytes(64));
        byte[] large = new byte[2048];

        // when & then: 상한을 넘는 순간 WORLD_TRACK_TOO_LARGE 로 거절
        assertRejected(ErrorCode.WORLD_TRACK_TOO_LARGE, () -> small.readCompressed("gzip", new ByteArrayInputStream(large)));
    }

    @Test
    @DisplayName("풀었을 때 상한을 넘는 압축 폭탄은 다 풀기 전에 WORLD_TRACK_TOO_LARGE 로 거절한다")
    void parse_rejects_body_over_decompressed_limit() {
        // given: 압축하면 작지만 풀면 1MB 인 본문, 풀었을 때 상한 64KB
        byte[] bomb = gzip(" ".repeat(1024 * 1024) + track("null"));
        assertThat(bomb.length).isLessThan(64 * 1024);

        // when & then: 다 풀기 전에 WORLD_TRACK_TOO_LARGE 로 거절
        assertRejected(ErrorCode.WORLD_TRACK_TOO_LARGE, () -> reader.parse(bomb));
    }

    @Test
    @DisplayName("gzip 형식이 깨진 본문은 INVALID_REQUEST 로 거절한다")
    void parse_rejects_broken_gzip() {
        // given: gzip 머리만 있고 뒤가 잘린 본문
        byte[] compressed = gzip(track("null"));
        byte[] truncated = Arrays.copyOf(compressed, compressed.length / 2);

        // when & then: gzip 이 아닌 바이트와 잘린 gzip 모두 INVALID_REQUEST
        assertInvalid(() -> reader.parse("not gzip".getBytes(StandardCharsets.UTF_8)));
        assertInvalid(() -> reader.parse(truncated));
    }

    @Test
    @DisplayName("viewerUserId 칸이 아예 없으면 INVALID_REQUEST, null 로 오면 공용 트랙으로 읽는다")
    void parse_distinguishes_missing_viewer_user_id_from_null() {
        // given: viewerUserId 칸을 아예 뺀 본문
        String missing = track("null").replace("\"viewerUserId\": null,", "");

        // when & then: 칸 없음은 거절, null 은 공용, 문자열은 그대로 읽힘
        assertInvalid(() -> reader.parse(gzip(missing)));
        assertThat(reader.parse(gzip(track("null"))).viewerUserId()).isNull();
        assertThat(reader.parse(gzip(track("\"12\""))).viewerUserId()).isEqualTo("12");
    }

    @Test
    @DisplayName("모르는 칸은 무시하고, 긴 엑스트라 번호는 글자 그대로 읽는다")
    void parse_ignores_unknown_fields_and_keeps_long_extra_ref() {
        // given: 맨 위와 등장인물에 처음 보는 칸, 80자리 엑스트라
        String body = track("null")
                .replace("\"actors\": []", """
                        "actors": [{"ref": "%s", "mood": "happy", "segments": []}], "weather": "rain\"""".formatted(LONG_EXTRA_REF));

        // when: 모르는 칸이 섞인 본문 읽기
        WorldTrackRequest request = reader.parse(gzip(body));

        // then: 모르는 칸은 무시되고 엑스트라 번호는 글자 그대로
        assertThat(request.actors()).hasSize(1);
        assertThat(request.actors().getFirst().ref()).isEqualTo(LONG_EXTRA_REF);
    }

    private static WorldTrackBodyReader readerWithLimits(DataSize compressed, DataSize decompressed) {
        return new WorldTrackBodyReader(
                new WorldTrackProperties(compressed, decompressed, 2),
                JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build());
    }

    private static void assertInvalid(ThrowingCallable call) {
        assertRejected(ErrorCode.INVALID_REQUEST, call);
    }

    private static void assertRejected(ErrorCode errorCode, ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(errorCode);
    }

    private static String track(String viewerUserId) {
        return """
                {"poolNumber": "3", "date": "2026-10-16", "sectorCode": "S034", "layoutVersion": "sha256:5f2c",
                 "viewerUserId": %s,
                 "actors": [], "talks": [], "gestures": [], "objects": [], "events": [], "items": []}
                """.formatted(viewerUserId);
    }

    private static byte[] gzip(String body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(body.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }
}
