package com.nidus.twinly.common.presign;

import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.aws.s3.S3Service;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PhotoCommitServiceUnitTest {

    @Mock
    S3Service s3Service;

    @Mock
    CloudFrontService cloudFrontService;

    @InjectMocks
    PhotoCommitService photoCommitService;

    @Test
    @DisplayName("본인 소유 key로 커밋하면 서명된 URL과 실제 업로드 크기를 반환한다")
    void commitProfilePhoto_success() {
        // given: 업로드가 끝나 S3에 크기가 잡히는 본인 소유 key
        given(s3Service.contentLength("profile/42/photo")).willReturn(Optional.of(2048L));
        given(cloudFrontService.getSignedUrl("profile/42/photo")).willReturn("https://cdn/profile/42/photo?sig=x");

        // when: 유저 42가 자기 key를 커밋
        PhotoCommitResult result = photoCommitService.commitProfilePhoto(42L, "profile/42/photo");

        // then: CDN 서명 URL과 S3가 알려준 바이트 수가 담긴다
        assertThat(result.photoUrl()).isEqualTo("https://cdn/profile/42/photo?sig=x");
        assertThat(result.sourceBytes()).isEqualTo(2048L);
    }

    @Test
    @DisplayName("남의 소유자 id가 들어간 key면 NOT_KEY_OWNER 예외가 발생하고 S3를 조회하지 않는다")
    void commitProfilePhoto_withOthersKey_throws() {
        // when & then: 유저 42가 유저 7의 key를 커밋하려 하면 소유권 검증에서 끊긴다
        assertThatThrownBy(() -> photoCommitService.commitProfilePhoto(42L, "profile/7/photo"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_KEY_OWNER);

        then(s3Service).should(never()).contentLength(anyString());
        then(cloudFrontService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("소유자 id가 접두사로만 같은 key(42 vs 420)는 남의 key로 간주해 거절한다")
    void commitProfilePhoto_withPrefixCollidingKey_throws() {
        // when & then: "profile/42/" 로 시작하지 않으므로 420의 key는 42의 것이 아니다
        assertThatThrownBy(() -> photoCommitService.commitProfilePhoto(42L, "profile/420/photo"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_KEY_OWNER);

        then(s3Service).should(never()).contentLength(anyString());
    }

    @Test
    @DisplayName("업로드가 끝나지 않아 S3에 객체가 없으면 UPLOAD_NOT_COMPLETED 예외가 발생한다")
    void commitProfilePhoto_withoutUploadedObject_throws() {
        // given: presign만 받고 실제 업로드는 하지 않은 상태
        given(s3Service.contentLength("profile/42/photo")).willReturn(Optional.empty());

        // when & then: 없는 객체를 커밋하면 업로드 미완료로 거절된다
        assertThatThrownBy(() -> photoCommitService.commitProfilePhoto(42L, "profile/42/photo"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UPLOAD_NOT_COMPLETED);

        then(cloudFrontService).shouldHaveNoInteractions();
    }
}
