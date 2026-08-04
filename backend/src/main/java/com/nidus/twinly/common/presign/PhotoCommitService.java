package com.nidus.twinly.common.presign;

import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.aws.s3.S3Service;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PhotoCommitService {

    private final S3Service s3Service;
    private final CloudFrontService cloudFrontService;

    public PhotoCommitResult commitProfilePhoto(Long ownerId, String key) {
        String expectedPrefix = "profile/%d/".formatted(ownerId);
        if (!key.startsWith(expectedPrefix)) {
            throw new BusinessException(ErrorCode.NOT_KEY_OWNER, "본인 소유의 key가 아닙니다: " + key);
        }

        long sourceBytes = s3Service.contentLength(key)
                .orElseThrow(() -> new BusinessException(ErrorCode.UPLOAD_NOT_COMPLETED, "업로드가 완료되지 않은 key입니다: " + key));

        return new PhotoCommitResult(cloudFrontService.getSignedUrl(key), sourceBytes);
    }
}