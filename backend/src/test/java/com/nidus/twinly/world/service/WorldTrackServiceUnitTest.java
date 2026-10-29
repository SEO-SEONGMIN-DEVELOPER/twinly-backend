package com.nidus.twinly.world.service;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.user.repository.UserRepository;
import com.nidus.twinly.world.dto.command.WorldTrackSaveCommand;
import com.nidus.twinly.world.dto.request.WorldTrackActorRequest;
import com.nidus.twinly.world.dto.request.WorldTrackEventRequest;
import com.nidus.twinly.world.dto.request.WorldTrackObjectChangeRequest;
import com.nidus.twinly.world.dto.request.WorldTrackObjectRequest;
import com.nidus.twinly.world.dto.request.WorldTrackRequest;
import com.nidus.twinly.world.dto.request.WorldTrackSegmentRequest;
import com.nidus.twinly.world.reader.WorldTrackBodyReader;
import com.nidus.twinly.world.repository.WorldTrackRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WorldTrackServiceUnitTest {

    private static final int POOL_NUMBER = 3;
    private static final LocalDate DATE = LocalDate.of(2026, 10, 16);
    private static final String SECTOR_CODE = "S034";
    private static final byte[] COMPRESSED = {1, 2, 3};
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Mock
    WorldTrackSavePermits worldTrackSavePermits;

    @Mock
    WorldTrackBodyReader worldTrackBodyReader;

    @Mock
    UserRepository userRepository;

    @Mock
    WorldTrackRepository worldTrackRepository;

    WorldTrackService worldTrackService;

    private final InputStream body = new ByteArrayInputStream(COMPRESSED);

    @BeforeEach
    void setUp() {
        worldTrackService = new WorldTrackService(worldTrackSavePermits, worldTrackBodyReader, VALIDATOR, userRepository, worldTrackRepository);
    }

    @Test
    @DisplayName("공용 트랙(viewerUserId null)은 viewer 0 으로, 받은 압축 바이트 그대로 저장하고 허가를 돌려준다")
    void saveTrack_saves_public_track_with_viewer_zero() {
        // given
        givenReadable(track(null));
        given(userRepository.existsByPoolNumber(POOL_NUMBER)).willReturn(true);

        // when
        worldTrackService.saveTrack(command());

        // then
        then(worldTrackRepository).should().upsert(POOL_NUMBER, DATE, SECTOR_CODE, 0L, COMPRESSED);
        then(worldTrackSavePermits).should().release();
    }

    @Test
    @DisplayName("개인 트랙은 viewerUserId 로 저장한다")
    void saveTrack_saves_personal_track_with_viewer_user_id() {
        // given
        givenReadable(track("12"));
        given(userRepository.existsByPoolNumber(POOL_NUMBER)).willReturn(true);
        given(userRepository.existsByIdAndDeletedAtIsNull(12L)).willReturn(true);

        // when
        worldTrackService.saveTrack(command());

        // then
        then(worldTrackRepository).should().upsert(POOL_NUMBER, DATE, SECTOR_CODE, 12L, COMPRESSED);
    }

    @Test
    @DisplayName("동시 저장 허가가 없으면 본문을 읽지 않고 WORLD_TRACK_SAVE_BUSY")
    void saveTrack_rejects_when_no_permit() {
        // given
        given(worldTrackSavePermits.tryAcquire()).willReturn(false);

        // when & then
        assertThatThrownBy(() -> worldTrackService.saveTrack(command()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.WORLD_TRACK_SAVE_BUSY);
        then(worldTrackBodyReader).shouldHaveNoInteractions();
        then(worldTrackSavePermits).should(never()).release();
    }

    @Test
    @DisplayName("본문을 읽다 실패해도 허가를 돌려준다")
    void saveTrack_releases_permit_when_reading_fails() {
        // given
        given(worldTrackSavePermits.tryAcquire()).willReturn(true);
        given(worldTrackBodyReader.readCompressed("gzip", body)).willThrow(new BusinessException(ErrorCode.INVALID_REQUEST));

        // when & then
        assertInvalidWithoutSave(() -> worldTrackService.saveTrack(command()));
        then(worldTrackSavePermits).should().release();
    }

    @Test
    @DisplayName("필수 배열 칸이 없거나 ref·시각 형식이 틀리면 INVALID_REQUEST")
    void saveTrack_rejects_format_violations() {
        // given: 배열 칸 없음, user: 뒤가 숫자가 아님, 오프셋 붙은 시각, viewerUserId 0
        WorldTrackRequest valid = track(null);
        List<WorldTrackRequest> invalids = List.of(
                new WorldTrackRequest("3", "2026-10-16", "S034", "sha256:abc", null, valid.actors(), null, List.of(), List.of(), List.of(), List.of()),
                withActors(List.of(new WorldTrackActorRequest("user:abc", List.of()))),
                withActors(List.of(new WorldTrackActorRequest("user:12", List.of(new WorldTrackSegmentRequest("2026-10-16T13:00:00+09:00", "2026-10-16T13:01:00"))))),
                new WorldTrackRequest("3", "2026-10-16", "S034", "sha256:abc", "0", List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                new WorldTrackRequest("3", "2026-10-16", "S034", "md5:abc", null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of())
        );

        for (WorldTrackRequest invalid : invalids) {
            givenReadable(invalid);

            // when & then
            assertInvalidWithoutSave(() -> worldTrackService.saveTrack(command()));
        }
    }

    @Test
    @DisplayName("경로와 본문의 풀·회차·구역이 다르면 INVALID_REQUEST")
    void saveTrack_rejects_path_body_mismatch() {
        // given
        List<WorldTrackRequest> mismatches = List.of(
                new WorldTrackRequest("4", "2026-10-16", "S034", "sha256:abc", null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                new WorldTrackRequest("3", "2026-10-17", "S034", "sha256:abc", null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                new WorldTrackRequest("3", "2026-10-16", "S035", "sha256:abc", null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of())
        );

        for (WorldTrackRequest mismatch : mismatches) {
            givenReadable(mismatch);

            // when & then
            assertInvalidWithoutSave(() -> worldTrackService.saveTrack(command()));
        }
    }

    @Test
    @DisplayName("시작이 끝보다 늦거나, 형식은 맞지만 없는 날짜면 INVALID_REQUEST")
    void saveTrack_rejects_reversed_or_impossible_times() {
        // given: 거꾸로 된 구간, 2월 30일 사건, 25시 사물 변화
        List<WorldTrackRequest> invalids = List.of(
                withActors(List.of(new WorldTrackActorRequest("user:12", List.of(new WorldTrackSegmentRequest("2026-10-16T13:01:00", "2026-10-16T13:00:00.500"))))),
                new WorldTrackRequest("3", "2026-10-16", "S034", "sha256:abc", null, List.of(), List.of(), List.of(), List.of(),
                        List.of(new WorldTrackEventRequest("C048", "P0103", "2026-02-30T14:02:00", "2026-02-30T14:02:00", List.of())), List.of()),
                new WorldTrackRequest("3", "2026-10-16", "S034", "sha256:abc", null, List.of(), List.of(), List.of(),
                        List.of(new WorldTrackObjectRequest("P0103-o3", List.of(new WorldTrackObjectChangeRequest("2026-10-16T25:00:00", "user:12")))), List.of(), List.of())
        );

        for (WorldTrackRequest invalid : invalids) {
            givenReadable(invalid);

            // when & then
            assertInvalidWithoutSave(() -> worldTrackService.saveTrack(command()));
        }
    }

    @Test
    @DisplayName("사용자가 없는 풀이면 INVALID_REQUEST")
    void saveTrack_rejects_unknown_pool() {
        // given
        givenReadable(track(null));
        given(userRepository.existsByPoolNumber(POOL_NUMBER)).willReturn(false);

        // when & then
        assertInvalidWithoutSave(() -> worldTrackService.saveTrack(command()));
    }

    @Test
    @DisplayName("없거나 탈퇴 처리된 사용자의 개인 트랙이면 INVALID_REQUEST")
    void saveTrack_rejects_unknown_viewer() {
        // given
        givenReadable(track("99"));
        given(userRepository.existsByPoolNumber(POOL_NUMBER)).willReturn(true);
        given(userRepository.existsByIdAndDeletedAtIsNull(99L)).willReturn(false);

        // when & then
        assertInvalidWithoutSave(() -> worldTrackService.saveTrack(command()));
    }

    private void givenReadable(WorldTrackRequest request) {
        given(worldTrackSavePermits.tryAcquire()).willReturn(true);
        given(worldTrackBodyReader.readCompressed("gzip", body)).willReturn(COMPRESSED);
        given(worldTrackBodyReader.parse(COMPRESSED)).willReturn(request);
    }

    private void assertInvalidWithoutSave(ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        then(worldTrackRepository).should(never()).upsert(anyInt(), any(), any(), anyLong(), any());
    }

    private WorldTrackSaveCommand command() {
        return new WorldTrackSaveCommand(POOL_NUMBER, DATE, SECTOR_CODE, "gzip", body);
    }

    private static WorldTrackRequest withActors(List<WorldTrackActorRequest> actors) {
        return new WorldTrackRequest("3", "2026-10-16", "S034", "sha256:abc", null, actors, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static WorldTrackRequest track(String viewerUserId) {
        return new WorldTrackRequest("3", "2026-10-16", "S034", "sha256:abc", viewerUserId,
                List.of(new WorldTrackActorRequest("extra:" + "1234567890".repeat(8),
                        List.of(new WorldTrackSegmentRequest("2026-10-16T13:00:00", "2026-10-16T13:00:00.833")))),
                List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
