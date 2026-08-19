package com.nidus.twinly.common.photo;

import com.nidus.twinly.common.aws.s3.S3Service;
import software.amazon.awssdk.services.s3.model.S3Exception;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ProfileThumbnailServiceUnitTest {

    private static final String SOURCE_KEY = "profile/1/abc";
    private static final long SOURCE_BYTES = 1024L;

    @Mock
    S3Service s3Service;

    @Mock
    ThumbnailGenerator thumbnailGenerator;

    @InjectMocks
    ProfileThumbnailService profileThumbnailService;

    @Test
    @DisplayName("생성 성공 시 원본 key에 접미사를 붙인 key로 업로드하고 그 key를 돌려준다")
    void generate_uploads_and_returns_key() throws IOException {
        // given: 정상 크기의 원본과 정상 변환
        given(thumbnailGenerator.generate(any(), any())).willReturn(new byte[]{1, 2, 3});

        // when: 썸네일 생성
        String thumbnailKey = profileThumbnailService.generate(SOURCE_KEY, position(), SOURCE_BYTES);

        // then: 원본 key를 그대로 유도한 key로 업로드되고 그 key가 반환된다
        assertThat(thumbnailKey).isEqualTo("profile/1/abc-thumb");
        then(s3Service).should().upload("profile/1/abc-thumb", new byte[]{1, 2, 3}, "image/jpeg");
    }

    @Test
    @DisplayName("원본이 허용 크기를 넘으면 내려받지 않고 null을 돌려준다")
    void generate_skips_oversized_source() {
        // when: 10MB를 넘는 크기로 썸네일 생성
        String thumbnailKey = profileThumbnailService.generate(SOURCE_KEY, position(), 11L * 1024 * 1024);

        // then: 다운로드조차 하지 않는다 (시간과 대역폭을 쓰기 전에 차단)
        assertThat(thumbnailKey).isNull();
        then(s3Service).should(never()).download(any(), any());
        then(s3Service).should(never()).upload(any(), any(), any());
    }

    @Test
    @DisplayName("크기를 모르고 호출하면 직접 조회해 처리한다")
    void generate_without_known_size_looks_it_up() throws IOException {
        // given: 호출부가 크기를 갖고 있지 않은 경우 (가입 시 익명 세션 사진 이관)
        given(s3Service.contentLength(SOURCE_KEY)).willReturn(Optional.of(SOURCE_BYTES));
        given(thumbnailGenerator.generate(any(), any())).willReturn(new byte[]{1});

        // when: 크기 없이 썸네일 생성
        String thumbnailKey = profileThumbnailService.generate(SOURCE_KEY, position());

        // then: 스스로 HEAD를 한 번 날려 크기를 얻고 정상 처리한다
        assertThat(thumbnailKey).isEqualTo("profile/1/abc-thumb");
        then(s3Service).should().contentLength(SOURCE_KEY);
    }

    @Test
    @DisplayName("크기를 모르는데 원본이 없으면 null을 돌려준다")
    void generate_without_known_size_returns_null_when_absent() {
        // given: S3에 객체가 없는 경우
        given(s3Service.contentLength(SOURCE_KEY)).willReturn(Optional.empty());

        // when: 크기 없이 썸네일 생성
        String thumbnailKey = profileThumbnailService.generate(SOURCE_KEY, position());

        // then: 내려받지 않고 조용히 포기한다
        assertThat(thumbnailKey).isNull();
        then(s3Service).should(never()).download(any(), any());
    }

    @Test
    @DisplayName("크기를 모르는데 원본 조회가 S3 예외로 실패해도 null을 돌려준다")
    void generate_without_known_size_returns_null_when_lookup_fails() {
        // given: 버킷 이름이 바뀌었거나 권한이 어긋나 HEAD가 NoSuchKey가 아닌 예외로 실패하는 상황
        given(s3Service.contentLength(SOURCE_KEY))
                .willThrow(S3Exception.builder().statusCode(403).message("Access Denied").build());

        // when: 크기 없이 썸네일 생성
        String thumbnailKey = profileThumbnailService.generate(SOURCE_KEY, position());

        // then: 썸네일만 포기한다 (아바타 장식 때문에 회원가입이 통째로 실패하면 안 된다)
        assertThat(thumbnailKey).isNull();
        then(s3Service).should(never()).download(any(), any());
    }

    @Test
    @DisplayName("변환에 실패해도 예외를 퍼뜨리지 않고 null을 돌려준다")
    void generate_swallows_failure() throws IOException {
        // given: 크롭 좌표가 잘못돼 변환이 실패하는 상황
        given(thumbnailGenerator.generate(any(), any())).willThrow(new IOException("크롭 영역이 원본을 벗어났습니다"));

        // when: 썸네일 생성
        String thumbnailKey = profileThumbnailService.generate(SOURCE_KEY, position(), SOURCE_BYTES);

        // then: 썸네일만 포기한다 (알림 장식 때문에 프로필 사진 등록이 실패하면 안 된다)
        assertThat(thumbnailKey).isNull();
        then(s3Service).should(never()).upload(any(), any(), any());
    }

    @Test
    @DisplayName("S3 다운로드가 실패해도 null을 돌려주고 임시 파일을 남기지 않는다")
    void generate_deletes_temp_file_on_failure() {
        // given: 다운로드 단계에서 터지는 상황
        ArgumentCaptor<Path> tempCaptor = ArgumentCaptor.forClass(Path.class);
        willThrow(new RuntimeException("S3 장애")).given(s3Service).download(eq(SOURCE_KEY), tempCaptor.capture());

        // when: 썸네일 생성
        String thumbnailKey = profileThumbnailService.generate(SOURCE_KEY, position(), SOURCE_BYTES);

        // then: 실패해도 임시 파일이 디스크에 남지 않는다 (쌓이면 디스크가 조용히 찬다)
        assertThat(thumbnailKey).isNull();
        assertThat(tempCaptor.getValue()).doesNotExist();
    }

    @Test
    @DisplayName("생성에 성공해도 임시 파일은 지워진다")
    void generate_deletes_temp_file_on_success() throws IOException {
        // given: 정상 흐름
        given(thumbnailGenerator.generate(any(), any())).willReturn(new byte[]{1});
        ArgumentCaptor<Path> tempCaptor = ArgumentCaptor.forClass(Path.class);

        // when: 썸네일 생성
        profileThumbnailService.generate(SOURCE_KEY, position(), SOURCE_BYTES);

        // then: 임시 파일이 남지 않는다
        then(s3Service).should().download(eq(SOURCE_KEY), tempCaptor.capture());
        assertThat(tempCaptor.getValue()).doesNotExist();
    }

    @Test
    @DisplayName("임시 파일은 내려받기 전에 만들어져 생성기에 그대로 전달된다")
    void generate_passes_downloaded_file_to_generator() throws IOException {
        // given: 정상 흐름
        given(thumbnailGenerator.generate(any(), any())).willReturn(new byte[]{1});

        ArgumentCaptor<Path> downloadCaptor = ArgumentCaptor.forClass(Path.class);
        ArgumentCaptor<Path> generateCaptor = ArgumentCaptor.forClass(Path.class);

        // when: 썸네일 생성
        profileThumbnailService.generate(SOURCE_KEY, position(), SOURCE_BYTES);

        // then: 내려받은 파일과 변환에 넘긴 파일이 같다 (원본이 힙을 거치지 않는다)
        then(s3Service).should().download(eq(SOURCE_KEY), downloadCaptor.capture());
        then(thumbnailGenerator).should().generate(generateCaptor.capture(), any());
        assertThat(generateCaptor.getValue()).isEqualTo(downloadCaptor.getValue());
    }

    private PhotoPosInfo position() {
        return new PhotoPosInfo(new PhotoPosInfo.StartPos(0, 0), 100, 100);
    }
}
