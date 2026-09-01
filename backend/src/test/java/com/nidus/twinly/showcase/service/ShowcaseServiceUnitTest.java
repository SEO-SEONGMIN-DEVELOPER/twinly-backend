package com.nidus.twinly.showcase.service;

import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.entity.ScenePartner;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.scene.SceneNameRenderer;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.showcase.dto.result.ShowcaseActionSceneResult;
import com.nidus.twinly.showcase.dto.result.ShowcaseBubbleLineResult;
import com.nidus.twinly.showcase.dto.result.ShowcaseDialogueSceneResult;
import com.nidus.twinly.showcase.dto.result.ShowcaseTodayResult;
import com.nidus.twinly.showcase.dto.result.ShowcaseUserInfoResult;
import com.nidus.twinly.showcase.entity.Showcase;
import com.nidus.twinly.showcase.repository.ShowcaseRepository;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ShowcaseServiceUnitTest {

    private static final Long VIEWER_ID = 12L;
    private static final Long TARGET_ID = 204L;
    private static final Long PARTNER_ID = 311L;

    @Mock
    ShowcaseRepository showcaseRepository;

    @Mock
    SceneRepository sceneRepository;

    @Mock
    ScenePartnerRepository scenePartnerRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    CurrentSeasonReader currentSeasonReader;

    // 이름 치환과 대사 파싱은 진짜 구현이 돌아야 마스킹 결과를 검증할 수 있다.
    @Spy
    SceneNameRenderer sceneNameRenderer = new SceneNameRenderer();

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    ShowcaseService showcaseService;

    @Test
    @DisplayName("오늘 배정이 없으면 후보 중 하나를 뽑아 저장한다")
    void today_assigns_target_when_absent() {
        // given: 오늘 배정이 없고 후보가 한 명뿐이다
        given(showcaseRepository.findByViewerUserIdAndDate(eq(VIEWER_ID), any())).willReturn(Optional.empty());
        given(currentSeasonReader.read()).willReturn(season());
        given(showcaseRepository.findAllTargetCandidateUserIds(anyLong(), anyLong(), any())).willReturn(List.of(TARGET_ID));
        given(showcaseRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        givenEmptyDay();

        // when: 오늘 관람 조회
        showcaseService.today(VIEWER_ID);

        // then: 관람자·대상·오늘 날짜로 배정이 저장된다
        ArgumentCaptor<Showcase> captor = ArgumentCaptor.forClass(Showcase.class);
        then(showcaseRepository).should().save(captor.capture());
        assertThat(captor.getValue().getViewerUserId()).isEqualTo(VIEWER_ID);
        assertThat(captor.getValue().getTargetUserId()).isEqualTo(TARGET_ID);
        assertThat(captor.getValue().getDate()).isEqualTo(KstTimes.today());
    }

    @Test
    @DisplayName("오늘 배정이 이미 있으면 후보를 다시 뽑지 않는다 (하루 고정)")
    void today_reuses_existing_assignment() {
        // given: 오늘 배정이 이미 있다
        given(showcaseRepository.findByViewerUserIdAndDate(eq(VIEWER_ID), any())).willReturn(Optional.of(showcase()));
        givenEmptyDay();

        // when: 같은 날 다시 조회
        ShowcaseTodayResult result = showcaseService.today(VIEWER_ID);

        // then: 기존 배정 id 반환 + 후보 조회·저장 없음
        assertThat(result.showcaseId()).isEqualTo(3312L);
        then(showcaseRepository).should(never()).findAllTargetCandidateUserIds(anyLong(), anyLong(), any());
        then(showcaseRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("후보가 없으면 SHOWCASE_TARGET_NOT_FOUND 예외가 발생하고 배정을 저장하지 않는다")
    void today_without_candidate_throws() {
        // given: 오늘 배정이 없고 후보도 없다
        given(showcaseRepository.findByViewerUserIdAndDate(eq(VIEWER_ID), any())).willReturn(Optional.empty());
        given(currentSeasonReader.read()).willReturn(season());
        given(showcaseRepository.findAllTargetCandidateUserIds(anyLong(), anyLong(), any())).willReturn(List.of());

        // when & then: 대상 없음 예외 + 배정 저장 안 함
        assertThatThrownBy(() -> showcaseService.today(VIEWER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SHOWCASE_TARGET_NOT_FOUND);

        then(showcaseRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("관람 대상은 userRef 1번, 등장 인물은 2번부터 받고 실제 유저 id는 나가지 않는다")
    void today_assigns_user_refs() {
        // given: 대상의 하루에 동행자 한 명이 등장하는 대화 씬이 있다
        given(showcaseRepository.findByViewerUserIdAndDate(eq(VIEWER_ID), any())).willReturn(Optional.of(showcase()));
        Scene dialogue = dialogueScene(88102L, """
                [{"t":"bubble","userId":311,"action":"웃으며","text":"여기 앉아.","occursAt":"2026-08-18T12:13:00"}]
                """);
        given(sceneRepository.findAllByUserIdAndDateOrderByStartsAtAsc(anyLong(), any())).willReturn(List.of(dialogue));
        given(scenePartnerRepository.findAllBySceneIdIn(anyList()))
                .willReturn(List.of(ScenePartner.create(88102L, PARTNER_ID)));
        given(userRepository.findAllById(any()))
                .willReturn(List.of(user(TARGET_ID, "김", "민수"), user(PARTNER_ID, "박", "지훈")));
        givenViewerCounts();

        // when: 오늘 관람 조회
        ShowcaseTodayResult result = showcaseService.today(VIEWER_ID);

        // then: 대상=1, 동행자=2로 매겨지고 응답 어디에도 실제 id가 없다
        assertThat(result.userRef()).isEqualTo(1L);
        assertThat(result.userInfos()).extracting(ShowcaseUserInfoResult::userRef).containsExactly(1L, 2L);

        ShowcaseDialogueSceneResult scene = (ShowcaseDialogueSceneResult) result.scenes().get(0);
        assertThat(scene.with()).containsExactly(2L);
        assertThat(((ShowcaseBubbleLineResult) scene.lines().get(0)).userRef()).isEqualTo(2L);
    }

    @Test
    @DisplayName("본문의 이름 자리는 성만 남긴 이름으로 치환된다")
    void today_masks_names_in_scene_text() {
        // given: 나레이션에 이름 자리가 들어 있는 행동 씬
        given(showcaseRepository.findByViewerUserIdAndDate(eq(VIEWER_ID), any())).willReturn(Optional.of(showcase()));
        given(sceneRepository.findAllByUserIdAndDateOrderByStartsAtAsc(anyLong(), any()))
                .willReturn(List.of(actionScene(88101L, "{user_204}이 뛰어서 등교했다.", "아슬아슬했다.")));
        given(scenePartnerRepository.findAllBySceneIdIn(anyList())).willReturn(List.of());
        given(userRepository.findAllById(any())).willReturn(List.of(user(TARGET_ID, "김", "민수")));
        givenViewerCounts();

        // when: 오늘 관람 조회
        ShowcaseTodayResult result = showcaseService.today(VIEWER_ID);

        // then: 이름(민수)이 아니라 성+OO(김OO)로 치환된다
        ShowcaseActionSceneResult scene = (ShowcaseActionSceneResult) result.scenes().get(0);
        assertThat(scene.narration()).isEqualTo("김OO이 뛰어서 등교했다.");
        assertThat(result.userInfos().get(0).userName()).isEqualTo("김OO");
    }

    @Test
    @DisplayName("소속이 대학교로 끝나면 학교를 떼고, 그 밖의 소속은 그대로 내려간다")
    void today_shortens_university_organization() {
        // given: 대상은 대학교, 동행자는 고등학교 소속이다
        given(showcaseRepository.findByViewerUserIdAndDate(eq(VIEWER_ID), any())).willReturn(Optional.of(showcase()));
        given(sceneRepository.findAllByUserIdAndDateOrderByStartsAtAsc(anyLong(), any()))
                .willReturn(List.of(actionScene(88101L, "등교했다.", null)));
        given(scenePartnerRepository.findAllBySceneIdIn(anyList()))
                .willReturn(List.of(ScenePartner.create(88101L, PARTNER_ID)));
        given(userRepository.findAllById(any()))
                .willReturn(List.of(user(TARGET_ID, "김", "민수", "고려대학교"), user(PARTNER_ID, "박", "지훈", "한국고등학교")));
        givenViewerCounts();

        // when: 오늘 관람 조회
        ShowcaseTodayResult result = showcaseService.today(VIEWER_ID);

        // then: 고려대학교 → 고려대, 한국고등학교는 그대로
        assertThat(result.userInfos()).extracting(ShowcaseUserInfoResult::organization)
                .containsExactly("고려대", "한국고등학교");
    }

    private void givenEmptyDay() {
        given(sceneRepository.findAllByUserIdAndDateOrderByStartsAtAsc(anyLong(), any())).willReturn(List.of());
        given(scenePartnerRepository.findAllBySceneIdIn(anyList())).willReturn(List.of());
        given(userRepository.findAllById(any())).willReturn(List.of(user(TARGET_ID, "김", "민수")));
        givenViewerCounts();
    }

    private void givenViewerCounts() {
        given(userRepository.findById(VIEWER_ID)).willReturn(Optional.of(user(VIEWER_ID, "이", "서연")));
        given(userRepository.countByDeletedAtIsNull()).willReturn(12840);
        given(userRepository.countByDeletedAtIsNullAndOrganizationHash(any())).willReturn(320);
    }

    private Showcase showcase() {
        Showcase showcase = Showcase.create(VIEWER_ID, TARGET_ID, KstTimes.today());
        ReflectionTestUtils.setField(showcase, "id", 3312L);

        return showcase;
    }

    private Season season() {
        Season season = Season.create(Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"));
        ReflectionTestUtils.setField(season, "id", 1L);

        return season;
    }

    private Scene actionScene(Long id, String narration, String mind) {
        Scene scene = Scene.createAction(TARGET_ID, LocalDate.parse("2026-08-18"), "v1", "학교 정문",
                LocalDateTime.parse("2026-08-18T09:00:00"), LocalDateTime.parse("2026-08-18T09:40:00"), narration, mind);
        ReflectionTestUtils.setField(scene, "id", id);

        return scene;
    }

    private Scene dialogueScene(Long id, String lines) {
        Scene scene = Scene.createDialogue(TARGET_ID, LocalDate.parse("2026-08-18"), "v1", "식당",
                LocalDateTime.parse("2026-08-18T12:10:00"), LocalDateTime.parse("2026-08-18T12:50:00"), lines);
        ReflectionTestUtils.setField(scene, "id", id);

        return scene;
    }

    private User user(Long id, String familyName, String givenName) {
        return user(id, familyName, givenName, "한국대학교");
    }

    private User user(Long id, String familyName, String givenName, String organization) {
        User user = User.create(
                "nick" + id,
                familyName, "familyHash",
                givenName, "givenHash",
                Gender.MALE,
                organization, "orgHash",
                "컴퓨터공학과", "affHash",
                "20", "affNoHash",
                "2000-01-01", "birthHash",
                "phone" + id, "phoneHash",
                "email" + id + "@test.com", "emailHash", null, null
        );
        ReflectionTestUtils.setField(user, "id", id);

        return user;
    }
}
