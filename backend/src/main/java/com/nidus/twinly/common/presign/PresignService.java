package com.nidus.twinly.common.presign;

import com.nidus.twinly.common.aws.s3.S3Service;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        if (!ALLOWED_PHOTO_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE, "지원하지 않는 이미지 형식입니다: " + contentType);
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