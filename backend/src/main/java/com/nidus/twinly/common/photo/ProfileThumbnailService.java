package com.nidus.twinly.common.photo;

import com.nidus.twinly.common.aws.s3.S3Service;
import com.nidus.twinly.common.logging.WarnLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileThumbnailService {

    private static final long MAX_SOURCE_BYTES = 10L * 1024 * 1024;
    private static final String THUMBNAIL_KEY_SUFFIX = "-thumb";
    private static final String THUMBNAIL_CONTENT_TYPE = "image/jpeg";
    private static final String PREVIEW_KEY_PREFIX = "preview/";
    private static final int PREVIEW_SIZE = 8;

    private final S3Service s3Service;
    private final ThumbnailGenerator thumbnailGenerator;

    public String generate(String sourceKey, PhotoPosInfo position) {
        try {
            return s3Service.contentLength(sourceKey)
                    .map(sourceBytes -> generate(sourceKey, position, sourceBytes))
                    .orElse(null);
        } catch (RuntimeException e) {
            WarnLog.log(log, "원본 정보를 읽지 못해 썸네일 없이 진행합니다.", e, field("key", sourceKey));
            return null;
        }
    }

    public String generate(String sourceKey, PhotoPosInfo position, long sourceBytes) {
        try {
            if (sourceBytes > MAX_SOURCE_BYTES) {
                WarnLog.log(log, "원본이 너무 커 썸네일을 건너뜁니다.", field("key", sourceKey), field("bytes", sourceBytes));
                return null;
            }

            return resize(sourceKey, sourceKey + THUMBNAIL_KEY_SUFFIX, source -> thumbnailGenerator.generate(source, position));
        } catch (IOException | RuntimeException e) {
            WarnLog.log(log, "썸네일 생성에 실패해 아바타 없이 진행합니다.", e, field("key", sourceKey));
            return null;
        }
    }

    public String generatePreview(String sourceKey, PhotoPosInfo position) {
        try {
            Optional<Long> sourceBytes = s3Service.contentLength(sourceKey);
            if (sourceBytes.isEmpty()) {
                WarnLog.log(log, "원본이 없어 미리보기 사진 없이 진행합니다.", field("key", sourceKey));
                return null;
            }

            if (sourceBytes.get() > MAX_SOURCE_BYTES) {
                WarnLog.log(log, "원본이 너무 커 미리보기 사진을 건너뜁니다.", field("key", sourceKey), field("bytes", sourceBytes.get()));
                return null;
            }

            return resize(sourceKey, PREVIEW_KEY_PREFIX + UUID.randomUUID(), source -> thumbnailGenerator.generate(source, position, PREVIEW_SIZE));
        } catch (IOException | RuntimeException e) {
            WarnLog.log(log, "미리보기 사진 생성에 실패해 사진 없이 진행합니다.", e, field("key", sourceKey));
            return null;
        }
    }

    private String resize(String sourceKey, String targetKey, Resizer resizer) throws IOException {
        try (TempFile temp = TempFile.create()) {
            s3Service.download(sourceKey, temp.path());
            s3Service.upload(targetKey, resizer.resize(temp.path()), THUMBNAIL_CONTENT_TYPE);

            return targetKey;
        }
    }

    @FunctionalInterface
    private interface Resizer {

        byte[] resize(Path source) throws IOException;
    }

    private record TempFile(Path path) implements AutoCloseable {

        private static TempFile create() throws IOException {
            return new TempFile(Files.createTempFile("thumbnail-", ".img"));
        }

        @Override
        public void close() {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                WarnLog.log(log, "임시 파일을 지우지 못했습니다.", e, field("path", path));
            }
        }
    }
}
