package com.nidus.twinly.activity.service;

import com.nidus.twinly.activity.domain.QuestionType;
import com.nidus.twinly.activity.domain.SceneType;
import com.nidus.twinly.activity.dto.result.ActivityActionSceneResult;
import com.nidus.twinly.common.scene.SceneBubbleLine;
import com.nidus.twinly.common.scene.SceneNarrationLine;
import com.nidus.twinly.activity.dto.result.ActivityDialogueSceneResult;
import com.nidus.twinly.activity.dto.result.ActivityQuestionResult;
import com.nidus.twinly.activity.dto.result.ActivityResult;
import com.nidus.twinly.activity.dto.result.ActivityUserInfoResult;
import com.nidus.twinly.activity.entity.Question;
import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.entity.ScenePartner;
import com.nidus.twinly.activity.repository.QuestionRepository;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.photo.PhotoPosInfo;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PhotoRepository;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ActivityServiceUnitTest {

    private static final Long USER_ID = 1L;
    private static final Long SEASON_ID = 7L;
    private static final LocalDate DATE = LocalDate.of(2026, 7, 26);
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    @Mock
    SceneRepository sceneRepository;

    @Mock
    ScenePartnerRepository scenePartnerRepository;

    @Mock
    QuestionRepository questionRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    PhotoRepository photoRepository;

    @Mock
    CloudFrontService cloudFrontService;

    @Mock
    CurrentSeasonReader currentSeasonReader;

    ActivityService activityService;

    @BeforeEach
    void setUp() {
        // lines(JSON) 파싱은 서비스의 실제 책임이므로 ObjectMapper는 mock이 아닌 실제 객체를 주입한다.
        activityService = new ActivityService(
                sceneRepository, scenePartnerRepository, questionRepository, userRepository, photoRepository, cloudFrontService, currentSeasonReader, new ObjectMapper());

        // 현재 시즌은 DB의 활성 시즌에서 읽어오므로 리포지토리 스텁으로 대체한다.
        Season currentSeason = BeanUtils.instantiateClass(Season.class);
        ReflectionTestUtils.setField(currentSeason, "id", SEASON_ID);
        given(currentSeasonReader.read()).willReturn(currentSeason);
    }

    @Test
    @DisplayName("action 씬은 타입/장소/나레이션/속마음과 KST 시각, 동행 유저 id까지 매핑하고 첫 씬의 version을 응답 version으로 쓴다")
    void activity_maps_action_scene() {
        // given: 파트너 1명이 있는 action 씬 1개와 그 파트너 유저가 존재
        Scene scene = actionScene(10L, "v1", "학교 복도",
                DATE.atTime(9, 0), DATE.atTime(10, 0),
                "복도를 천천히 걸었다", "조금 설레었다");
        given(sceneRepository.findAllByUserIdAndDate(USER_ID, DATE)).willReturn(List.of(scene));
        given(scenePartnerRepository.findAllBySceneIdIn(List.of(10L))).willReturn(List.of(scenePartner(10L, 100L)));
        given(userRepository.findAllById(List.of(USER_ID, 100L)))
                .willReturn(List.of(user(USER_ID, "나", "자신"), user(100L, "홍", "길동")));
        given(questionRepository.findAllByUserIdAndDate(USER_ID, DATE)).willReturn(List.of());

        // when: 활동 조회
        ActivityResult result = activityService.activity(USER_ID, DATE);

        // then: 메타 정보(userId/seasonId/date/version)와 action 씬 필드가 그대로 매핑된다
        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.seasonId()).isEqualTo(SEASON_ID);
        assertThat(result.date()).isEqualTo(DATE);
        assertThat(result.version()).isEqualTo("v1");
        assertThat(result.scenes()).hasSize(1);

        ActivityActionSceneResult action = (ActivityActionSceneResult) result.scenes().get(0);
        assertThat(action.sceneId()).isEqualTo(10L);
        assertThat(action.type()).isEqualTo("action");
        assertThat(action.startsAt()).isEqualTo(OffsetDateTime.of(2026, 7, 26, 9, 0, 0, 0, KST));
        assertThat(action.endsAt()).isEqualTo(OffsetDateTime.of(2026, 7, 26, 10, 0, 0, 0, KST));
        assertThat(action.place()).isEqualTo("학교 복도");
        assertThat(action.narration()).isEqualTo("복도를 천천히 걸었다");
        assertThat(action.mind()).isEqualTo("조금 설레었다");
        assertThat(action.with()).containsExactly(100L);
        assertThat(result.userInfos()).containsExactly(
                new ActivityUserInfoResult(USER_ID, "자신", null),
                new ActivityUserInfoResult(100L, "길동", null));
    }

    @Test
    @DisplayName("date 다음 날 새벽에 일어난 씬도 해당 회차에 포함되고 실제 날짜의 KST 시각으로 내려간다")
    void activity_maps_scene_after_midnight() {
        // given: 7/26 회차에 속하지만 실제로는 7/27 새벽에 일어난 씬
        Scene scene = actionScene(10L, "v1", "편의점",
                DATE.plusDays(1).atTime(1, 30), DATE.plusDays(1).atTime(2, 0),
                "야식을 샀다", "출출했다");
        given(sceneRepository.findAllByUserIdAndDate(USER_ID, DATE)).willReturn(List.of(scene));
        given(userRepository.findAllById(List.of(USER_ID))).willReturn(List.of(user(USER_ID, "나", "자신")));
        given(questionRepository.findAllByUserIdAndDate(USER_ID, DATE)).willReturn(List.of());

        // when: 7/26 회차 조회
        ActivityResult result = activityService.activity(USER_ID, DATE);

        // then: date가 아니라 씬에 저장된 실제 시각이 그대로 변환된다
        ActivityActionSceneResult action = (ActivityActionSceneResult) result.scenes().get(0);
        assertThat(action.startsAt()).isEqualTo(OffsetDateTime.of(2026, 7, 27, 1, 30, 0, 0, KST));
        assertThat(action.endsAt()).isEqualTo(OffsetDateTime.of(2026, 7, 27, 2, 0, 0, 0, KST));
    }

    @Test
    @DisplayName("dialogue 씬의 lines JSON은 narr/bubble 타입으로 구분되어 파싱된다")
    void activity_parses_dialogue_lines() {
        // given: narr 1줄 + bubble 1줄이 담긴 lines JSON을 가진 dialogue 씬
        String linesJson = """
                [
                  {"t":"narr","text":"교실이 조용해졌다","occursAt":"2026-07-26T12:00:00"},
                  {"t":"bubble","userId":100,"action":"웃으며","text":"안녕","occursAt":"2026-07-26T12:05:00"}
                ]
                """;
        Scene scene = dialogueScene(11L, "v1", "교실",
                DATE.atTime(12, 0), DATE.atTime(12, 30), linesJson);
        given(sceneRepository.findAllByUserIdAndDate(USER_ID, DATE)).willReturn(List.of(scene));
        given(userRepository.findAllById(List.of(USER_ID))).willReturn(List.of(user(USER_ID, "나", "자신")));
        given(questionRepository.findAllByUserIdAndDate(USER_ID, DATE)).willReturn(List.of());

        // when: 활동 조회
        ActivityResult result = activityService.activity(USER_ID, DATE);

        // then: lines가 순서대로 나레이션/말풍선 타입으로 역직렬화된다
        ActivityDialogueSceneResult dialogue = (ActivityDialogueSceneResult) result.scenes().get(0);
        assertThat(dialogue.type()).isEqualTo("dialogue");
        assertThat(dialogue.lines()).hasSize(2);

        SceneNarrationLine narration = (SceneNarrationLine) dialogue.lines().get(0);
        assertThat(narration.text()).isEqualTo("교실이 조용해졌다");
        assertThat(narration.occursAt()).isEqualTo(OffsetDateTime.of(2026, 7, 26, 12, 0, 0, 0, KST));

        SceneBubbleLine bubble = (SceneBubbleLine) dialogue.lines().get(1);
        assertThat(bubble.userId()).isEqualTo(100L);
        assertThat(bubble.action()).isEqualTo("웃으며");
        assertThat(bubble.text()).isEqualTo("안녕");
        assertThat(bubble.occursAt()).isEqualTo(OffsetDateTime.of(2026, 7, 26, 12, 5, 0, 0, KST));
    }

    /*
     * [멘토링 피드백]
     * 정상 / 실패 / 경계값
     * 잘못된 input이 실패하는지도 테스트로 작성
     */

    @Test
    @DisplayName("lines JSON이 손상되어도 그 씬의 대사만 비우고 하루치 조회는 성공한다")
    void activity_broken_lines_json_does_not_fail_whole_day() {
        // given: lines에 배열이 아닌 JSON이 저장된 dialogue 씬
        Scene broken = dialogueScene(11L, "v1", "교실",
                DATE.atTime(12, 0), DATE.atTime(12, 30), "{\"not\":\"an array\"}");
        given(sceneRepository.findAllByUserIdAndDate(USER_ID, DATE)).willReturn(List.of(broken));
        given(userRepository.findAllById(List.of(USER_ID))).willReturn(List.of(user(USER_ID, "나", "자신")));
        given(questionRepository.findAllByUserIdAndDate(USER_ID, DATE)).willReturn(List.of());

        // when: 활동 조회
        ActivityResult result = activityService.activity(USER_ID, DATE);

        // then: 500 대신 그 씬의 lines만 비고 나머지 필드는 정상 매핑된다
        ActivityDialogueSceneResult dialogue = (ActivityDialogueSceneResult) result.scenes().get(0);
        assertThat(dialogue.lines()).isEmpty();
        assertThat(dialogue.place()).isEqualTo("교실");
    }

    @Test
    @DisplayName("dialogue 씬의 lines가 null이면 빈 리스트로 매핑한다")
    void activity_dialogue_with_null_lines_returns_empty_lines() {
        // given: lines가 저장되지 않은 dialogue 씬
        Scene scene = dialogueScene(11L, "v1", "교실",
                DATE.atTime(12, 0), DATE.atTime(12, 30), null);
        given(sceneRepository.findAllByUserIdAndDate(USER_ID, DATE)).willReturn(List.of(scene));
        given(userRepository.findAllById(List.of(USER_ID))).willReturn(List.of(user(USER_ID, "나", "자신")));
        given(questionRepository.findAllByUserIdAndDate(USER_ID, DATE)).willReturn(List.of());

        // when: 활동 조회
        ActivityResult result = activityService.activity(USER_ID, DATE);

        // then: NPE 없이 빈 lines로 응답한다
        ActivityDialogueSceneResult dialogue = (ActivityDialogueSceneResult) result.scenes().get(0);
        assertThat(dialogue.lines()).isEmpty();
    }

    @Test
    @DisplayName("질문은 저장된 시각을 KST 시각으로 변환하고 타입을 문자열로 내보낸다")
    void activity_maps_questions() {
        // given: 씬은 없고 PROMISE 질문 1개만 존재
        given(sceneRepository.findAllByUserIdAndDate(USER_ID, DATE)).willReturn(List.of());
        given(userRepository.findAllById(List.of(USER_ID))).willReturn(List.of(user(USER_ID, "나", "자신")));
        given(questionRepository.findAllByUserIdAndDate(USER_ID, DATE))
                .willReturn(List.of(question(50L, DATE.atTime(21, 30), QuestionType.PROMISE,
                        "오늘 어땠어?", List.of("좋았어", "별로야"))));

        // when: 활동 조회
        ActivityResult result = activityService.activity(USER_ID, DATE);

        // then: 질문의 id/type/시각/본문/선택지가 매핑된다
        assertThat(result.questions()).hasSize(1);

        ActivityQuestionResult question = result.questions().get(0);
        assertThat(question.id()).isEqualTo(50L);
        assertThat(question.type()).isEqualTo("PROMISE");
        assertThat(question.time()).isEqualTo(OffsetDateTime.of(2026, 7, 26, 21, 30, 0, 0, KST));
        assertThat(question.text()).isEqualTo("오늘 어땠어?");
        assertThat(question.options()).containsExactly("좋았어", "별로야");
    }

    @Test
    @DisplayName("해당 날짜에 씬이 없으면 version은 null이고 빈 sceneId 목록으로 동행자를 조회한다")
    void activity_without_scenes_returns_null_version() {
        // given: 해당 날짜에 씬도 질문도 없음
        given(sceneRepository.findAllByUserIdAndDate(USER_ID, DATE)).willReturn(List.of());
        given(userRepository.findAllById(List.of(USER_ID))).willReturn(List.of(user(USER_ID, "나", "자신")));
        given(questionRepository.findAllByUserIdAndDate(USER_ID, DATE)).willReturn(List.of());

        // when: 활동 조회
        ActivityResult result = activityService.activity(USER_ID, DATE);

        // then: version은 null, 목록은 비어 있고 동행자 조회는 빈 sceneId 목록으로 위임된다
        assertThat(result.version()).isNull();
        assertThat(result.scenes()).isEmpty();
        assertThat(result.questions()).isEmpty();
        then(scenePartnerRepository).should().findAllBySceneIdIn(List.of());
    }

    @Test
    @DisplayName("userInfos는 with에 등장한 유저별로 한 건씩 담고, 사진이 없는 유저는 profilePhoto가 null이다")
    void activity_maps_user_infos_per_partner() {
        // given: 두 씬에 걸쳐 파트너 100(사진 있음), 200(사진 없음)이 등장하고 100은 두 씬 모두에 등장
        Scene first = actionScene(10L, "v1", "학교 복도",
                DATE.atTime(9, 0), DATE.atTime(10, 0), "걸었다", "설레었다");
        Scene second = actionScene(11L, "v1", "교실",
                DATE.atTime(11, 0), DATE.atTime(12, 0), "앉았다", "무덤덤했다");
        given(sceneRepository.findAllByUserIdAndDate(USER_ID, DATE)).willReturn(List.of(first, second));
        given(scenePartnerRepository.findAllBySceneIdIn(List.of(10L, 11L)))
                .willReturn(List.of(scenePartner(10L, 100L), scenePartner(11L, 100L), scenePartner(11L, 200L)));
        given(userRepository.findAllById(List.of(USER_ID, 100L, 200L)))
                .willReturn(List.of(user(USER_ID, "나", "자신"), user(100L, "홍", "길동"), user(200L, "김", "철수")));
        given(photoRepository.findAllByUserIdInAndType(List.of(USER_ID, 100L, 200L), PhotoType.PROFILE))
                .willReturn(List.of(Photo.create(100L, PhotoType.PROFILE, "profile/100/key", 10, 20, 100, 200, Instant.now())));
        given(cloudFrontService.getSignedUrl("profile/100/key")).willReturn("https://cdn.example.com/signed100");
        given(questionRepository.findAllByUserIdAndDate(USER_ID, DATE)).willReturn(List.of());

        // when: 활동 조회
        ActivityResult result = activityService.activity(USER_ID, DATE);

        // then: 조회자 본인이 맨 앞에 오고, 여러 씬에 중복 등장한 유저도 한 건이며, 이름과 key/서명 URL이 함께 담긴다
        assertThat(result.userInfos()).containsExactly(
                new ActivityUserInfoResult(USER_ID, "자신", null),
                new ActivityUserInfoResult(100L, "길동", new ProfilePhotoInfo("profile/100/key", "https://cdn.example.com/signed100",
                        new PhotoPosInfo(new PhotoPosInfo.StartPos(10, 20), 100, 200))),
                new ActivityUserInfoResult(200L, "철수", null));
    }

    @Test
    @DisplayName("동행 유저가 없는 action 씬은 with가 null이고 userInfos에는 조회자 본인만 담긴다")
    void activity_without_partners_returns_null_with() {
        // given: 동행자가 없는 씬 1개
        Scene scene = actionScene(10L, "v1", "집",
                DATE.atTime(9, 0), DATE.atTime(10, 0), "쉬었다", "편안했다");
        given(sceneRepository.findAllByUserIdAndDate(USER_ID, DATE)).willReturn(List.of(scene));
        given(userRepository.findAllById(List.of(USER_ID))).willReturn(List.of(user(USER_ID, "나", "자신")));
        given(questionRepository.findAllByUserIdAndDate(USER_ID, DATE)).willReturn(List.of());

        // when: 활동 조회
        ActivityResult result = activityService.activity(USER_ID, DATE);

        // then: 빈 배열이 아니라 null로 내려가고, 본인 정보는 그대로 담긴다
        ActivityActionSceneResult action = (ActivityActionSceneResult) result.scenes().get(0);
        assertThat(action.with()).isNull();
        assertThat(result.userInfos()).containsExactly(new ActivityUserInfoResult(USER_ID, "자신", null));
    }

    private Scene actionScene(Long id, String version, String place,
                              LocalDateTime startsAt, LocalDateTime endsAt,
                              String narration, String mind) {
        Scene scene = newScene(id, version, place, startsAt, endsAt, SceneType.ACTION);
        ReflectionTestUtils.setField(scene, "narration", narration);
        ReflectionTestUtils.setField(scene, "mind", mind);
        return scene;
    }

    private Scene dialogueScene(Long id, String version, String place,
                                LocalDateTime startsAt, LocalDateTime endsAt, String lines) {
        Scene scene = newScene(id, version, place, startsAt, endsAt, SceneType.DIALOGUE);
        ReflectionTestUtils.setField(scene, "lines", lines, String.class);
        return scene;
    }

    // Scene은 protected 기본 생성자만 있으므로 리플렉션으로 인스턴스를 만들고 필드를 채운다.
    private Scene newScene(Long id, String version, String place,
                           LocalDateTime startsAt, LocalDateTime endsAt, SceneType type) {
        Scene scene = BeanUtils.instantiateClass(Scene.class);
        ReflectionTestUtils.setField(scene, "id", id);
        ReflectionTestUtils.setField(scene, "userId", USER_ID);
        ReflectionTestUtils.setField(scene, "date", DATE);
        ReflectionTestUtils.setField(scene, "version", version);
        ReflectionTestUtils.setField(scene, "place", place);
        ReflectionTestUtils.setField(scene, "startsAt", startsAt);
        ReflectionTestUtils.setField(scene, "endsAt", endsAt);
        ReflectionTestUtils.setField(scene, "type", type);
        ReflectionTestUtils.setField(scene, "createdAt", Instant.now());
        return scene;
    }

    private ScenePartner scenePartner(Long sceneId, Long partnerUserId) {
        ScenePartner scenePartner = BeanUtils.instantiateClass(ScenePartner.class);
        ReflectionTestUtils.setField(scenePartner, "sceneId", sceneId);
        ReflectionTestUtils.setField(scenePartner, "userId", partnerUserId);
        ReflectionTestUtils.setField(scenePartner, "createdAt", Instant.now());
        return scenePartner;
    }

    private Question question(Long id, LocalDateTime time, QuestionType type, String text, List<String> options) {
        Question question = BeanUtils.instantiateClass(Question.class);
        ReflectionTestUtils.setField(question, "id", id);
        ReflectionTestUtils.setField(question, "userId", USER_ID);
        ReflectionTestUtils.setField(question, "date", DATE);
        ReflectionTestUtils.setField(question, "time", time);
        ReflectionTestUtils.setField(question, "type", type);
        ReflectionTestUtils.setField(question, "text", text);
        ReflectionTestUtils.setField(question, "options", options);
        ReflectionTestUtils.setField(question, "isSkipped", false);
        ReflectionTestUtils.setField(question, "createdAt", Instant.now());
        return question;
    }

    private User user(Long id, String familyName, String givenName) {
        User user = User.create(
                "nick", familyName, "familyHash", givenName, "givenHash",
                Gender.MALE, "organization", "organizationHash", "aff", "affHash", "affNo", "affNoHash",
                "2000-01-01", "birthHash", "phone", "phoneHash", "email", "emailHash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
