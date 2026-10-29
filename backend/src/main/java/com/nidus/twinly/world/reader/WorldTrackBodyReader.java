package com.nidus.twinly.world.reader;

import com.nidus.twinly.common.logging.WarnLog;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.world.config.WorldTrackProperties;
import com.nidus.twinly.world.dto.request.WorldTrackRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorldTrackBodyReader {

    private static final String GZIP = "gzip";

    private final WorldTrackProperties worldTrackProperties;
    private final ObjectMapper objectMapper;

    public byte[] readCompressed(String contentEncoding, InputStream body) {
        if (!GZIP.equalsIgnoreCase(contentEncoding)) {
            WarnLog.log(log, "gzip 으로 압축하지 않은 세계 트랙을 거절합니다.", field("contentEncoding", contentEncoding));
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.ACCEPT_ENCODING, GZIP);
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, headers);
        }

        try (InputStream limited = new SizeLimitedInputStream(body, worldTrackProperties.maxCompressedSize().toBytes())) {
            return limited.readAllBytes();
        } catch (IOException e) {
            throw rejected("세계 트랙 압축 본문을 읽지 못했습니다.", e);
        }
    }

    public WorldTrackRequest parse(byte[] compressed) {
        try (InputStream decompressed = new SizeLimitedInputStream(
                new GZIPInputStream(new ByteArrayInputStream(compressed)),
                worldTrackProperties.maxDecompressedSize().toBytes())) {
            return objectMapper.readValue(decompressed, WorldTrackRequest.class);
        } catch (IOException | JacksonException e) {
            throw rejected("세계 트랙 본문을 풀거나 읽지 못했습니다.", e);
        }
    }

    private BusinessException rejected(String message, Exception e) {
        ErrorCode errorCode = isSizeLimitExceeded(e) ? ErrorCode.WORLD_TRACK_TOO_LARGE : ErrorCode.INVALID_REQUEST;
        WarnLog.log(log, message, e, field("errorCode", errorCode));
        return new BusinessException(errorCode, e);
    }

    private boolean isSizeLimitExceeded(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SizeLimitExceededException) {
                return true;
            }
        }
        return false;
    }
}
