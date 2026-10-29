package com.nidus.twinly.world.integration;

import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.service.WithdrawnUserChunkDeletionService;
import com.nidus.twinly.world.entity.WorldTrack;
import com.nidus.twinly.world.repository.WorldTrackRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorldTrackIntegrationTest extends AbstractIntegrationTest {

    private static final int POOL_NUMBER = 3;
    private static final String DATE = "2026-10-16";
    private static final String SECTOR_CODE = "S034";
    // 엑스트라 번호는 70자리가 넘을 수 있어 숫자로 읽으면 끝자리가 뭉개진다
    private static final String LONG_EXTRA_REF = "extra:" + "1234567890".repeat(8);

    @Autowired
    WorldTrackRepository worldTrackRepository;

    @Autowired
    WithdrawnUserChunkDeletionService withdrawnUserChunkDeletionService;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    @DisplayName("gzip 공용 트랙을 받으면 200 이고, 받은 압축 바이트 그대로 viewer 0 으로 저장한다")
    void saveTrack_stores_public_track_bytes_as_received() throws Exception {
        // given: 풀 3 사용자, 밀리초 시각·긴 엑스트라 번호·처음 보는 칸이 든 공용 트랙
        User user = saveUserInPool(POOL_NUMBER);
        byte[] body = gzip(track("null", user.getId(), "\"weather\": \"rain\","));

        // when: gzip 헤더와 압축 본문으로 저장
        save(body).andExpect(status().isOk());
        flushAndClear();

        // then: 행 하나, 바이트가 한 글자도 바뀌지 않았다
        List<WorldTrack> tracks = worldTrackRepository.findAll();
        assertThat(tracks).hasSize(1);
        WorldTrack track = tracks.getFirst();
        assertThat(track.getPoolNumber()).isEqualTo(POOL_NUMBER);
        assertThat(track.getDate()).isEqualTo(LocalDate.parse(DATE));
        assertThat(track.getSectorCode()).isEqualTo(SECTOR_CODE);
        assertThat(track.getViewerUserId()).isEqualTo(WorldTrack.PUBLIC_VIEWER_USER_ID);
        assertThat(track.getBody()).isEqualTo(body);
        assertThat(track.getReceivedAt()).isNotNull();
    }

    @Test
    @DisplayName("같은 열쇠로 다시 보내면 통째로 바꾸고, 개인 트랙은 공용 트랙을 덮지 않는다")
    void saveTrack_replaces_same_key_and_keeps_personal_track_apart() throws Exception {
        // given: 공용 트랙을 한 번 저장
        User viewer = saveUserInPool(POOL_NUMBER);
        save(gzip(track("null", viewer.getId(), ""))).andExpect(status().isOk());

        // when: 같은 공용 열쇠로 다른 본문, 같은 구역의 개인 트랙
        byte[] resent = gzip(track("null", viewer.getId(), "\"resent\": true,"));
        byte[] personal = gzip(track("\"%d\"".formatted(viewer.getId()), viewer.getId(), ""));
        save(resent).andExpect(status().isOk());
        save(personal).andExpect(status().isOk());
        flushAndClear();

        // then: 공용 1행(새 본문) + 개인 1행
        List<WorldTrack> tracks = worldTrackRepository.findAll();
        assertThat(tracks).hasSize(2);
        assertThat(tracks).filteredOn(t -> t.getViewerUserId() == WorldTrack.PUBLIC_VIEWER_USER_ID)
                .singleElement().extracting(WorldTrack::getBody).isEqualTo(resent);
        assertThat(tracks).filteredOn(t -> t.getViewerUserId().equals(viewer.getId()))
                .singleElement().extracting(WorldTrack::getBody).isEqualTo(personal);
    }

    @Test
    @DisplayName("배열 칸이 없거나, 경로와 본문이 다르거나, viewer 0·없는 풀이면 400 이고 저장하지 않는다")
    void saveTrack_rejects_invalid_tracks() throws Exception {
        // given: 풀 3 사용자와 정상 본문
        User user = saveUserInPool(POOL_NUMBER);
        String valid = track("null", user.getId(), "");

        // when & then: items 칸 없음, 본문 구역이 경로와 다름, viewerUserId "0"
        for (String invalid : List.of(
                valid.replace("\"items\": []", "\"noItems\": []"),
                valid.replace("\"sectorCode\": \"S034\"", "\"sectorCode\": \"S035\""),
                valid.replace("\"viewerUserId\": null", "\"viewerUserId\": \"0\""))) {
            save(gzip(invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REQUEST.name()));
        }

        // when & then: 사용자가 없는 풀
        mockMvc.perform(put("/internal/v1/pools/{poolNumber}/tracks/{date}/{sectorCode}", 99, DATE, SECTOR_CODE)
                        .header(HttpHeaders.CONTENT_ENCODING, "gzip")
                        .content(gzip(valid.replace("\"poolNumber\": \"3\"", "\"poolNumber\": \"99\""))))
                .andExpect(status().isBadRequest());

        assertThat(worldTrackRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("gzip 이 아니면 415 와 Accept-Encoding: gzip, 풀었을 때 상한을 넘으면 413 이고 저장하지 않는다")
    void saveTrack_rejects_plain_body_with_415_and_too_large_body_with_413() throws Exception {
        // given: 풀 3 사용자
        User user = saveUserInPool(POOL_NUMBER);

        // when & then: gzip 아님
        mockMvc.perform(put("/internal/v1/pools/{poolNumber}/tracks/{date}/{sectorCode}", POOL_NUMBER, DATE, SECTOR_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(track("null", user.getId(), "")))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string(HttpHeaders.ACCEPT_ENCODING, "gzip"))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNSUPPORTED_MEDIA_TYPE.name()));

        // when & then: 압축하면 작지만 풀면 설정 상한(60MB)을 넘는 본문
        save(gzipSpaces(DataSize.ofMegabytes(61)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value(ErrorCode.WORLD_TRACK_TOO_LARGE.name()));

        assertThat(worldTrackRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("탈퇴 회원 파기 때 그 사람의 개인 트랙만 지우고 공용 트랙은 남긴다")
    void withdrawal_deletes_only_personal_tracks_of_withdrawn_user() throws Exception {
        // given: 공용 트랙 + 탈퇴할 사람의 개인 트랙
        User viewer = saveUserInPool(POOL_NUMBER);
        save(gzip(track("null", viewer.getId(), ""))).andExpect(status().isOk());
        save(gzip(track("\"%d\"".formatted(viewer.getId()), viewer.getId(), ""))).andExpect(status().isOk());
        viewer = userRepository.findById(viewer.getId()).orElseThrow();
        viewer.requestWithdrawal(Duration.ZERO);
        flushAndClear();

        // when: 탈퇴 회원 파기 실행
        withdrawnUserChunkDeletionService.deleteChunk(Instant.now().plusSeconds(1), 500);
        flushAndClear();

        // then: 공용 트랙만 남는다
        assertThat(worldTrackRepository.findAll())
                .extracting(WorldTrack::getViewerUserId)
                .containsExactly(WorldTrack.PUBLIC_VIEWER_USER_ID);
    }

    private User saveUserInPool(int poolNumber) {
        User user = saveUser();
        userRepository.assignPoolNumber(user.getId(), poolNumber);
        flushAndClear();
        return user;
    }

    private ResultActions save(byte[] body) throws Exception {
        return mockMvc.perform(put("/internal/v1/pools/{poolNumber}/tracks/{date}/{sectorCode}", POOL_NUMBER, DATE, SECTOR_CODE)
                .header(HttpHeaders.CONTENT_ENCODING, "gzip")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static String track(String viewerUserId, Long userId, String extraField) {
        return """
                {"poolNumber": "3", "date": "2026-10-16", "sectorCode": "S034", "layoutVersion": "sha256:5f2c",
                 %s"viewerUserId": %s,
                 "actors": [
                   {"ref": "user:%d", "segments": [
                     {"start": "2026-10-16T13:02:10", "end": "2026-10-16T13:03:40.833", "pose": "walk", "placeCode": "P0279",
                      "path": [[4,18],[4,9],[12,9]], "activity": "이동", "motion": "walk"}]},
                   {"ref": "%s", "segments": [
                     {"start": "2026-10-16T08:30:00", "end": "2026-10-16T19:00:00", "pose": "stand", "placeCode": "P0103",
                      "tile": [20,4], "seatId": null, "activity": "업무", "motion": null}]}
                 ],
                 "talks": [{"start": "2026-10-16T13:20:00", "end": "2026-10-16T13:20:04", "placeCode": "P0103", "members": ["%s", "user:%d"]}],
                 "gestures": [],
                 "objects": [{"objectId": "P0103-o3", "kind": "창문", "initial": "닫힘",
                              "changes": [{"at": "2026-10-16T13:41:00", "state": "열림", "by": "card:C048"}]}],
                 "events": [{"cardId": "C048", "placeCode": "P0103", "start": "2026-10-16T14:02:00", "end": "2026-10-16T14:02:00", "extras": []}],
                 "items": []}
                """.formatted(extraField, viewerUserId, userId, LONG_EXTRA_REF, LONG_EXTRA_REF, userId);
    }

    private static byte[] gzip(String body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(body.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    private static byte[] gzipSpaces(DataSize size) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = " ".repeat(1024 * 1024).getBytes(StandardCharsets.UTF_8);
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            for (long written = 0; written < size.toBytes(); written += chunk.length) {
                gzip.write(chunk);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }
}
