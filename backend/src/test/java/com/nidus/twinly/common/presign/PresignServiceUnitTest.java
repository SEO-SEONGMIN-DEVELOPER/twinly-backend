package com.nidus.twinly.common.presign;

import com.nidus.twinly.common.aws.s3.S3Service;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PresignServiceUnitTest {

    @Mock
    S3Service s3Service;

    @InjectMocks
    PresignService presignService;

    @Test
    @DisplayName("허용된 이미지 형식이면 소유자·타입별 key로 presign URL을 발급한다")
    void presignPhoto_success() {
        // given: S3가 presign URL을 돌려주도록 설정
        given(s3Service.presignPut(anyString(), anyString(), any(Duration.class)))
                .willReturn("https://bucket.s3/presigned");

        // when: 유저 42의 프로필 사진 presign 요청
        PhotoPresignResult result = presignService.presignPhoto(42L, "image/jpeg", PhotoType.PROFILE);

        // then: key는 "타입(소문자)/소유자id/랜덤" 형태이고, 응답에 업로드 조건이 함께 담긴다
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        then(s3Service).should().presignPut(keyCaptor.capture(), eq("image/jpeg"), any(Duration.class));
        assertThat(keyCaptor.getValue()).startsWith("profile/42/");

        assertThat(result.uploadUrl()).isEqualTo("https://bucket.s3/presigned");
        assertThat(result.key()).isEqualTo(keyCaptor.getValue());
        assertThat(result.method()).isEqualTo("PUT");
        assertThat(result.requiredHeaders().contentType()).isEqualTo("image/jpeg");
        assertThat(result.maxBytes()).isEqualTo(10 * 1024 * 1024);
        assertThat(result.expiresAt()).isNotNull();
    }

    @Test
    @DisplayName("같은 소유자가 두 번 요청해도 key가 겹치지 않는다")
    void presignPhoto_generatesDistinctKeys() {
        // given: S3가 presign URL을 돌려주도록 설정
        given(s3Service.presignPut(anyString(), anyString(), any(Duration.class)))
                .willReturn("https://bucket.s3/presigned");

        // when: 같은 유저가 같은 타입으로 두 번 요청
        String first = presignService.presignPhoto(42L, "image/png", PhotoType.PROFILE).key();
        String second = presignService.presignPhoto(42L, "image/png", PhotoType.PROFILE).key();

        // then: 랜덤 파트가 달라 서로 덮어쓰지 않는다
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("contentType이 없으면 INVALID_REQUEST 예외가 발생하고 presign을 시도하지 않는다")
    void presignPhoto_withoutContentType_throws() {
        // when & then: contentType null 이면 검증 단계에서 끊긴다
        assertThatThrownBy(() -> presignService.presignPhoto(42L, null, PhotoType.PROFILE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        then(s3Service).should(never()).presignPut(anyString(), anyString(), any(Duration.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/gif", "application/pdf", "text/plain", "image/jpeg; charset=utf-8"})
    @DisplayName("허용 목록에 없는 형식이면 UNSUPPORTED_IMAGE_TYPE 예외가 발생하고 presign을 시도하지 않는다")
    void presignPhoto_withUnsupportedContentType_throws(String contentType) {
        // when & then: 허용 목록(jpeg/png/webp) 밖이면 그대로 거절된다
        assertThatThrownBy(() -> presignService.presignPhoto(42L, contentType, PhotoType.PROFILE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNSUPPORTED_IMAGE_TYPE);

        then(s3Service).should(never()).presignPut(anyString(), anyString(), any(Duration.class));
    }
}
