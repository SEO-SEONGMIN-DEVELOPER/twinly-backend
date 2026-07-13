package com.nidus.twinly.common.presign;

import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.aws.s3.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PhotoCommitService {

    private final S3Service s3Service;
    private final CloudFrontService cloudFrontService;

    public String commitProfilePhoto(Long ownerId, String key) {
        String expectedPrefix = "profile-photos/%d/".formatted(ownerId);
        if (!key.startsWith(expectedPrefix)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 소유의 key가 아닙니다: " + key);
        }

        if (!s3Service.exists(key)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "업로드가 완료되지 않은 key입니다: " + key);
        }

        return cloudFrontService.getSignedUrl(key);
    }
}