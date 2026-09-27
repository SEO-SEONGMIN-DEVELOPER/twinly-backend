package com.nidus.twinly.user.seed;

import com.nidus.twinly.common.aws.s3.S3Service;
import com.nidus.twinly.common.photo.PhotoPosInfo;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.photo.ProfileThumbnailService;
import com.nidus.twinly.common.photo.ThumbnailGenerator;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShowcasePhotoSeederUnitTest {

    private static final int SHOWCASE_USER_COUNT = 20;

    @Mock
    PhotoRepository photoRepository;

    @Mock
    S3Service s3Service;

    @Mock
    ProfileThumbnailService profileThumbnailService;

    @InjectMocks
    ShowcasePhotoSeeder showcasePhotoSeeder;

    List<User> users;

    @BeforeEach
    void setUp() {
        users = LongStream.rangeClosed(1, SHOWCASE_USER_COUNT).mapToObj(ShowcasePhotoSeederUnitTest::user).toList();
        given(profileThumbnailService.generate(anyString(), any(), anyLong()))
                .willAnswer(invocation -> invocation.getArgument(0) + "-thumb");
    }

    @Test
    @DisplayName("사진이 없는 쇼케이스 유저마다 원본을 올리고 썸네일과 함께 프로필 사진을 저장한다")
    void seed_uploads_and_saves_profile_photos() {
        // given: 아무도 프로필 사진이 없는 상태
        given(photoRepository.findAllByUserIdInAndType(any(), eq(PhotoType.PROFILE))).willReturn(List.of());

        // when: 사진 시드
        showcasePhotoSeeder.seed(users);

        // then: 20명 모두 본인 경로에 원본이 올라가고, 썸네일 key 가 채워진 프로필 사진이 저장된다
        then(s3Service).should(times(SHOWCASE_USER_COUNT)).upload(anyString(), any(), eq("image/webp"));
        ArgumentCaptor<Photo> captor = ArgumentCaptor.forClass(Photo.class);
        then(photoRepository).should(times(SHOWCASE_USER_COUNT)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(photo -> {
            assertThat(photo.getType()).isEqualTo(PhotoType.PROFILE);
            assertThat(photo.getKey()).startsWith("profile/%d/seed-".formatted(photo.getUserId()));
            assertThat(photo.getThumbnailKey()).isEqualTo(photo.getKey() + "-thumb");
        });
    }

    @Test
    @DisplayName("이미 같은 시드 사진과 썸네일이 있으면 아무것도 올리거나 저장하지 않는다")
    void seed_is_idempotent() {
        // given: 한 번 시드한 결과를 그대로 가진 상태
        given(photoRepository.findAllByUserIdInAndType(any(), eq(PhotoType.PROFILE))).willReturn(List.of());
        showcasePhotoSeeder.seed(users);
        ArgumentCaptor<Photo> captor = ArgumentCaptor.forClass(Photo.class);
        then(photoRepository).should(times(SHOWCASE_USER_COUNT)).save(captor.capture());
        given(photoRepository.findAllByUserIdInAndType(any(), eq(PhotoType.PROFILE))).willReturn(captor.getAllValues());

        // when: 다시 사진 시드
        showcasePhotoSeeder.seed(users);

        // then: 추가 업로드·저장이 없다
        then(s3Service).should(times(SHOWCASE_USER_COUNT)).upload(anyString(), any(), anyString());
        then(photoRepository).should(times(SHOWCASE_USER_COUNT)).save(any());
    }

    @Test
    @DisplayName("다른 사진을 쓰고 있던 쇼케이스 유저는 기존 행을 시드 사진으로 바꾼다")
    void seed_replaces_existing_photo() {
        // given: 1번 유저가 다른 사진을 쓰고 있는 상태
        Photo existing = Photo.create(1L, PhotoType.PROFILE, "profile/1/other", 0, 0, 10, 10, null);
        ReflectionTestUtils.setField(existing, "id", 99L);
        given(photoRepository.findAllByUserIdInAndType(any(), eq(PhotoType.PROFILE))).willReturn(List.of(existing));

        // when: 사진 시드
        showcasePhotoSeeder.seed(users);

        // then: 새 행을 만들지 않고 기존 행의 key 를 시드 사진으로 바꾼다
        assertThat(existing.getId()).isEqualTo(99L);
        assertThat(existing.getKey()).startsWith("profile/1/seed-");
        then(photoRepository).should().save(existing);
    }

    @Test
    @DisplayName("한 명의 업로드가 실패해도 나머지 유저의 사진은 채운다")
    void seed_continues_when_one_upload_fails() {
        // given: 1번 유저 경로 업로드만 실패하는 상태
        given(photoRepository.findAllByUserIdInAndType(any(), eq(PhotoType.PROFILE))).willReturn(List.of());
        willThrow(new RuntimeException("s3 down")).given(s3Service)
                .upload(startsWith("profile/1/"), any(), anyString());

        // when: 사진 시드
        showcasePhotoSeeder.seed(users);

        // then: 나머지 19명은 저장된다
        then(photoRepository).should(times(SHOWCASE_USER_COUNT - 1)).save(any());
        then(photoRepository).should(never()).save(argThat(photo -> photo.getUserId() == 1L));
    }

    @Test
    @DisplayName("쇼케이스 사진 리소스 20장은 가운데 정사각형 크롭으로 썸네일까지 만들 수 있다")
    void resources_have_center_square_crop(@TempDir Path dir) throws IOException {
        for (int number = 1; number <= SHOWCASE_USER_COUNT; number++) {
            byte[] image;
            try (InputStream in = new ClassPathResource(ShowcasePhotoSeeder.RESOURCE_PATTERN.formatted(number)).getInputStream()) {
                image = in.readAllBytes();
            }

            PhotoPosInfo position = ShowcasePhotoSeeder.centerSquare(image);

            assertThat(position.width()).isEqualTo(position.height()).isPositive();
            assertThat(position.startPos().x()).isNotNegative();
            assertThat(position.startPos().y()).isNotNegative();

            Path source = Files.write(dir.resolve(number + ".webp"), image);
            assertThat(new ThumbnailGenerator().generate(source, position)).isNotEmpty();
        }
    }

    private static User user(long id) {
        User user = mock(User.class);
        given(user.getId()).willReturn(id);
        return user;
    }
}
