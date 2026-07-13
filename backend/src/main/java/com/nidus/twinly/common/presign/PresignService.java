package com.nidus.twinly.common.presign;

import com.nidus.twinly.common.aws.s3.S3Service;
import com.nidus.twinly.common.photo.PhotoType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PresignService {

    private static final Set<String> ALLOWED_PHOTO_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Integer PHOTO_MAX_BYTES = 10 * 1024 * 1024;
    private static final Duration URL_EXPIRES_IN = Duration.ofSeconds(300);

    private final S3Service s3Service;

    public PhotoPresignResult presignPhoto(Long ownerId, String contentType, PhotoType type) {
        if (contentType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
        }

        if (!ALLOWED_PHOTO_CONTENT_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 이미지 형식입니다: " + contentType);
        }

        String key = "%s/%d/%s".formatted(type.name().toLowerCase(), ownerId, UUID.randomUUID());

        String presignedUrl = s3Service.presignPut(key, contentType, URL_EXPIRES_IN);

        return new PhotoPresignResult(
                presignedUrl, key, "PUT",
                new RequiredHeaders(contentType),
                PHOTO_MAX_BYTES,
                Instant.now().plus(URL_EXPIRES_IN)
        );
    }
}