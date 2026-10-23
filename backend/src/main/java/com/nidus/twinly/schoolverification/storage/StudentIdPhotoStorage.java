package com.nidus.twinly.schoolverification.storage;

import com.nidus.twinly.common.aws.s3.S3Service;
import com.nidus.twinly.common.presign.PhotoPresignResult;
import com.nidus.twinly.common.presign.RequiredHeaders;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StudentIdPhotoStorage {

    private static final String CONTENT_TYPE = "image/jpeg";
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final Duration UPLOAD_URL_EXPIRES_IN = Duration.ofSeconds(300);
    private static final Duration REVIEW_URL_EXPIRES_IN = Duration.ofMinutes(5);
    private static final String UPLOAD_PREFIX = "student-id/upload/";
    private static final String REVIEW_PREFIX = "student-id/review/";

    private final S3Service s3Service;

    public PhotoPresignResult presign(StudentIdPhotoOwner owner, String contentType) {
        if (!CONTENT_TYPE.equals(contentType)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE, "지원하지 않는 이미지 형식입니다: " + contentType);
        }

        String key = uploadPrefix(owner) + UUID.randomUUID();
        String uploadUrl = s3Service.presignPut(key, CONTENT_TYPE, UPLOAD_URL_EXPIRES_IN);

        return new PhotoPresignResult(
                uploadUrl, key, "PUT",
                new RequiredHeaders(CONTENT_TYPE),
                MAX_BYTES,
                Instant.now().plus(UPLOAD_URL_EXPIRES_IN)
        );
    }

    public void requireUploaded(StudentIdPhotoOwner owner, String uploadKey) {
        if (!uploadKey.startsWith(uploadPrefix(owner))) {
            throw new BusinessException(ErrorCode.STUDENT_ID_PHOTO_NOT_FOUND, "이 세션의 학생증 key가 아닙니다: " + uploadKey);
        }

        long bytes = s3Service.contentLength(uploadKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_ID_PHOTO_NOT_FOUND, "업로드되지 않은 학생증 key입니다: " + uploadKey));

        if (bytes > MAX_BYTES) {
            throw new BusinessException(ErrorCode.STUDENT_ID_PHOTO_TOO_LARGE, "학생증 사진 크기가 한도를 넘었습니다: " + bytes);
        }
    }

    public String copyToReview(String uploadKey, UUID submissionId) {
        String reviewKey = REVIEW_PREFIX + submissionId;

        s3Service.copy(uploadKey, reviewKey);

        return reviewKey;
    }

    public String reviewUrl(String reviewKey) {
        return s3Service.presignGet(reviewKey, REVIEW_URL_EXPIRES_IN);
    }

    public void delete(String uploadKey, String reviewKey) {
        s3Service.delete(reviewKey);
        s3Service.delete(uploadKey);
    }

    private String uploadPrefix(StudentIdPhotoOwner owner) {
        return UPLOAD_PREFIX + owner.pathSegment() + "/";
    }
}
