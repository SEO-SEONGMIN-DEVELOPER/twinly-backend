package com.nidus.twinly.people.service;

import com.nidus.twinly.activity.domain.SceneType;
import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.entity.ScenePartner;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.chat.entity.ChatRoom;
import com.nidus.twinly.chat.repository.ChatRoomRepository;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.photo.PhotoPosInfo;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.match.entity.Match;
import com.nidus.twinly.match.repository.MatchRepository;
import com.nidus.twinly.common.scene.SceneNarrationLine;
import com.nidus.twinly.people.domain.IntimacyResolution;
import com.nidus.twinly.people.dto.result.PeopleEventActionSceneResult;
import com.nidus.twinly.people.dto.result.PeopleEventResult;
import com.nidus.twinly.people.dto.result.PeopleEventUserInfoResult;
import com.nidus.twinly.people.dto.result.PeopleEventsResult;
import com.nidus.twinly.people.dto.result.PeopleIntimacySeriesItemResult;
import com.nidus.twinly.people.dto.result.PeopleIntimacySeriesResult;
import com.nidus.twinly.people.dto.result.PeopleItemResult;
import com.nidus.twinly.people.dto.result.PeopleLearnedFactsResult;
import com.nidus.twinly.people.dto.result.PeopleProfileResult;
import com.nidus.twinly.people.dto.result.PeopleResult;
import com.nidus.twinly.people.entity.Encounter;
import com.nidus.twinly.people.entity.EncounterPreference;
import com.nidus.twinly.people.repository.EncounterPreferenceRepository;
import com.nidus.twinly.people.repository.EncounterRepository;
import com.nidus.twinly.relationship.domain.RelationshipSpecificType;
import com.nidus.twinly.relationship.domain.RelationshipType;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.user.domain.DisclosureField;
import com.nidus.twinly.user.entity.DisclosureAgreement;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.DisclosureAgreementRepository;
import com.nidus.twinly.user.repository.PhotoRepository;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PeopleServiceUnitTest {

    private static final Long ME = 1L;

    @Mock
    UserRepository userRepository;

    @Mock
    PhotoRepository photoRepository;

    @Mock
    MatchRepository matchRepository;

    @Mock
    ChatRoomRepository chatRoomRepository;

    @Mock
    ScenePartnerRepository scenePartnerRepository;

    @Mock
    SceneRepository sceneRepository;

    @Mock
    RelationshipRepository relationshipRepository;

    @Mock
    EncounterRepository encounterRepository;

    @Mock
    EncounterPreferenceRepository encounterPreferenceRepository;

    @Mock
    DisclosureAgreementRepository disclosureAgreementRepository;

    @Mock
    BlockRepository blockRepository;

    @Mock
    CloudFrontService cloudFrontService;

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    PeopleService peopleService;

    // ------------------------------------------------------------------ people()

    @Test
    @DisplayName("사람 목록은 사진·친밀도·씬 수·채팅방·즐겨찾기를 파트너별로 결합해 반환한다")
    void people_merges_all_sources() {
        // given: 파트너 2명(10,20) 중 10만 사진·매칭/채팅방·씬·즐겨찾기가 있는 상태
        given(relationshipRepository.findPartnerUserIdsByUserId(ME, null, 21))
                .willReturn(List.of(10L, 20L));
        given(userRepository.findAllById(List.of(10L, 20L)))
                .willReturn(List.of(user(10L, "홍", "길동"), user(20L, "김", "철수")));
        given(photoRepository.findAllByUserIdInAndType(List.of(10L, 20L), PhotoType.PROFILE))
                .willReturn(List.of(Photo.create(10L, PhotoType.PROFILE, "key10", 10, 20, 100, 200, Instant.now())));
        given(cloudFrontService.getSignedUrl("key10")).willReturn("https://cdn.example.com/signed10");
        given(relationshipRepository.findLatestByUserIdAndPartnerUserIdIn(ME, List.of(10L, 20L)))
                .willReturn(List.of(
                        relationship(ME, 10L, LocalDate.of(2026, 7, 20), 40, "{}"),
                        relationship(ME, 20L, LocalDate.of(2026, 7, 20), 80, "{}")));
        given(matchRepository.findAllByUserIdAndPartnerUserIdIn(ME, List.of(10L, 20L)))
                .willReturn(List.of(match(5L, ME, 10L)));
        given(chatRoomRepository.findAllByMatchIdIn(List.of(5L)))
                .willReturn(List.of(chatRoom(7L, 5L)));
        given(scenePartnerRepository.countScenesByUserIdAndPartnerUserIdIn(ME, List.of(10L, 20L)))
                .willReturn(List.of(sceneCount(10L, 3L)));
        given(encounterRepository.findAllByUserIdAndPartnerUserIdIn(ME, List.of(10L, 20L)))
                .willReturn(List.of(encounter(9L, ME, 10L)));
        given(encounterPreferenceRepository.findAllByEncounterIdInAndUserId(List.of(9L), ME))
                .willReturn(List.of(preference(9L, ME, true)));

        // when: 사람 목록 조회
        PeopleResult result = peopleService.people(ME, null, null);

        // then: 파트너별로 각 소스가 결합되고 없는 값은 기본값(0/null/false)으로 채워짐
        assertThat(result.people()).hasSize(2);
        assertThat(result.people().get(0).userId()).isEqualTo(10L);
        assertThat(result.people().get(0).userName()).isEqualTo("길동");
        assertThat(result.people().get(0).profilePhoto().photoUrl()).isEqualTo("https://cdn.example.com/signed10");
        assertThat(result.people().get(0).profilePhoto().position())
                .isEqualTo(new PhotoPosInfo(new PhotoPosInfo.StartPos(10, 20), 100, 200));
        assertThat(result.people().get(0).intimacy()).isEqualTo(40);
        assertThat(result.people().get(0).relationshipType()).isEqualTo(RelationshipType.FRIEND);
        assertThat(result.people().get(0).relationshipSpecificType()).isEqualTo(RelationshipSpecificType.RELATIONSHIP_SPECIFIC_TYPE_2);
        assertThat(result.people().get(0).sceneElementCount()).isEqualTo(3);
        assertThat(result.people().get(0).chatRoomId()).isEqualTo(7L);
        assertThat(result.people().get(0).isFavorited()).isTrue();

        assertThat(result.people().get(1).userId()).isEqualTo(20L);
        assertThat(result.people().get(1).profilePhoto()).isNull();
        assertThat(result.people().get(1).relationshipType()).isEqualTo(RelationshipType.BEST_FRIEND);
        assertThat(result.people().get(1).sceneElementCount()).isZero();
        assertThat(result.people().get(1).chatRoomId()).isNull();
        assertThat(result.people().get(1).isFavorited()).isFalse();

        assertThat(result.page().hasMore()).isFalse();
        assertThat(result.page().nextCursor()).isNull();
    }

    @Test
    @DisplayName("limit보다 한 건 더 조회되면 초과분을 잘라내고 마지막 파트너 id를 nextCursor로 돌려준다")
    void people_hasMore_boundary() {
        // given: limit 2에 대해 3건이 조회된 상태(다음 페이지 존재)
        given(relationshipRepository.findPartnerUserIdsByUserId(ME, null, 3))
                .willReturn(List.of(10L, 20L, 30L));
        given(userRepository.findAllById(List.of(10L, 20L)))
                .willReturn(List.of(user(10L, "홍", "길동"), user(20L, "김", "철수")));

        // when: limit 2로 사람 목록 조회
        PeopleResult result = peopleService.people(ME, null, 2);

        // then: 2건만 반환하고 hasMore=true, nextCursor는 페이지의 마지막 파트너 id
        assertThat(result.people()).extracting(PeopleItemResult::userId).containsExactly(10L, 20L);
        assertThat(result.page().hasMore()).isTrue();
        assertThat(result.page().nextCursor()).isEqualTo(20L);
    }

    @Test
    @DisplayName("목록에서 탈퇴한 파트너는 사진 조회 대상에서 빠져 photo가 null이 된다")
    void people_excludes_withdrawn_partner_from_photo_lookup() {
        // given: 파트너 2명 중 20이 탈퇴한 상태
        User withdrawn = user(20L, "김", "철수");
        ReflectionTestUtils.setField(withdrawn, "deletedAt", Instant.now());
        given(relationshipRepository.findPartnerUserIdsByUserId(ME, null, 21)).willReturn(List.of(10L, 20L));
        given(userRepository.findAllById(List.of(10L, 20L)))
                .willReturn(List.of(user(10L, "홍", "길동"), withdrawn));
        given(photoRepository.findAllByUserIdInAndType(List.of(10L), PhotoType.PROFILE))
                .willReturn(List.of(Photo.create(10L, PhotoType.PROFILE, "key10", 10, 20, 100, 200, Instant.now())));
        given(cloudFrontService.getSignedUrl("key10")).willReturn("https://cdn.example.com/signed10");

        // when: 사람 목록 조회
        PeopleResult result = peopleService.people(ME, null, null);

        // then: 탈퇴한 파트너는 목록에 남되 이름·사진이 가려진다
        assertThat(result.people()).extracting(PeopleItemResult::userName)
                .containsExactly("길동", User.WITHDRAWN_NAME);
        assertThat(result.people().get(0).profilePhoto()).isNotNull();
        assertThat(result.people().get(1).profilePhoto()).isNull();
    }

    @Test
    @DisplayName("관계된 파트너가 없으면 빈 목록을 반환하고 부가 정보를 조회하지 않는다")
    void people_empty_short_circuits() {
        // given: 관계된 파트너가 하나도 없는 상태
        given(relationshipRepository.findPartnerUserIdsByUserId(ME, null, 21))
                .willReturn(List.of());

        // when: 사람 목록 조회
        PeopleResult result = peopleService.people(ME, null, null);

        // then: 빈 목록 + 페이지 기본값, 그리고 유저/사진 등 부가 조회는 수행하지 않음
        assertThat(result.people()).isEmpty();
        assertThat(result.page().hasMore()).isFalse();
        assertThat(result.page().nextCursor()).isNull();
        then(userRepository).should(never()).findAllById(any());
        then(photoRepository).should(never()).findAllByUserIdInAndType(any(), any());
    }

    // ----------------------------------------------------------------- profile()

    @Test
    @DisplayName("존재하지 않는 상대의 프로필을 조회하면 USER_NOT_FOUND 예외가 발생한다")
    void profile_user_not_found() {
        // when & then: 상대 유저가 없으면 USER_NOT_FOUND 예외 발생
        assertThatThrownBy(() -> peopleService.profile(ME, 20L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("프로필은 성+이름을 합치고 공개 동의된 필드만 노출하며 차단/즐겨찾기 상태를 반영한다")
    void profile_discloses_only_agreed_fields() {
        // given: 소속만 공개 동의하고, 즐겨찾기·차단된 상대
        User partner = user(20L, "김", "철수");
        ReflectionTestUtils.setField(partner, "affiliation", "트윈리대학교");
        ReflectionTestUtils.setField(partner, "affiliationNumber", "20260001");
        given(userRepository.findById(20L)).willReturn(Optional.of(partner));
        given(relationshipRepository.findLatestByUserIdAndPartnerUserId(ME, 20L))
                .willReturn(Optional.of(relationship(ME, 20L, LocalDate.of(2026, 7, 20), 75, "{}")));
        given(encounterRepository.findByUserAIdAndUserBId(ME, 20L)).willReturn(Optional.of(encounter(9L, ME, 20L)));
        given(encounterPreferenceRepository.findByEncounterIdAndUserId(9L, ME))
                .willReturn(Optional.of(preference(9L, ME, true)));
        given(blockRepository.existsByUserIdAndBlockedUserId(ME, 20L)).willReturn(true);
        given(disclosureAgreementRepository.findAllByUserId(20L))
                .willReturn(List.of(DisclosureAgreement.create(20L, DisclosureField.AFFILIATION)));

        // when: 프로필 조회
        PeopleProfileResult result = peopleService.profile(ME, 20L);

        // then: 이름 결합 + 동의 필드만 노출 + 즐겨찾기/차단 반영
        assertThat(result.userName()).isEqualTo("김철수");
        assertThat(result.intimacy()).isEqualTo(75);
        assertThat(result.relationshipType()).isEqualTo(RelationshipType.BEST_FRIEND);
        assertThat(result.disclosedFields().affiliation()).isEqualTo("트윈리대학교");
        assertThat(result.disclosedFields().affiliationNumber()).isNull();
        assertThat(result.isFavorited()).isTrue();
        assertThat(result.isBlocked()).isTrue();
        assertThat(result.isDeleted()).isFalse();
        assertThat(result.profilePhoto()).isNull();
    }

    @Test
    @DisplayName("탈퇴한 상대의 프로필은 이름·사진·공개 필드를 모두 가리고 조회조차 하지 않는다")
    void profile_of_withdrawn_partner_is_masked() {
        // given: 소속을 공개 동의했지만 탈퇴한 상대
        User partner = user(20L, "김", "철수");
        ReflectionTestUtils.setField(partner, "affiliation", "트윈리대학교");
        ReflectionTestUtils.setField(partner, "deletedAt", Instant.now());
        given(userRepository.findById(20L)).willReturn(Optional.of(partner));
        given(relationshipRepository.findLatestByUserIdAndPartnerUserId(ME, 20L)).willReturn(Optional.empty());
        given(encounterRepository.findByUserAIdAndUserBId(ME, 20L)).willReturn(Optional.empty());

        // when: 프로필 조회
        PeopleProfileResult result = peopleService.profile(ME, 20L);

        // then: block 도메인과 같은 문구로 마스킹되고 isDeleted로도 구분할 수 있다
        assertThat(result.userName()).isEqualTo(User.WITHDRAWN_NAME);
        assertThat(result.isDeleted()).isTrue();
        assertThat(result.profilePhoto()).isNull();
        assertThat(result.disclosedFields().affiliation()).isNull();
        assertThat(result.disclosedFields().affiliationNumber()).isNull();

        // then: 가려질 값이므로 사진·공개 동의는 조회하지 않는다
        then(photoRepository).should(never()).findByUserIdAndType(eq(20L), any());
        then(disclosureAgreementRepository).should(never()).findAllByUserId(20L);
    }

    // ------------------------------------------------- favorite() / deleteFavorite()

    @Test
    @DisplayName("만난 적 없는 상대를 즐겨찾기하면 ENCOUNTER_NOT_FOUND 예외가 발생하고 저장하지 않는다")
    void favorite_encounter_not_found() {
        // when & then: encounter가 없으면 ENCOUNTER_NOT_FOUND 예외 발생 + 저장 안 함
        assertThatThrownBy(() -> peopleService.favorite(ME, 20L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ENCOUNTER_NOT_FOUND);

        then(encounterPreferenceRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("즐겨찾기 등록은 조회 없이 upsert 한 번으로 위임한다")
    void favorite_delegates_to_upsert() {
        // given: encounter가 존재하는 상태
        given(encounterRepository.findByUserAIdAndUserBId(ME, 20L)).willReturn(Optional.of(encounter(9L, ME, 20L)));

        // when: 즐겨찾기 등록
        peopleService.favorite(ME, 20L);

        // then: 조회-후-저장이 아니라 원자적 upsert 한 번 (하트 연타가 유니크 제약을 위반하지 않는다)
        then(encounterPreferenceRepository).should().upsertIsFavorited(9L, ME, true);
        then(encounterPreferenceRepository).should(never()).findByEncounterIdAndUserId(any(), any());
        then(encounterPreferenceRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("즐겨찾기 해제는 기존 EncounterPreference를 isFavorited=false로 바꿔 저장한다")
    void deleteFavorites_updates_preference() {
        // given: 이미 즐겨찾기된 상태
        given(encounterRepository.findByUserAIdAndUserBId(ME, 20L)).willReturn(Optional.of(encounter(9L, ME, 20L)));
        given(encounterRepository.findByUserAIdAndUserBId(ME, 20L)).willReturn(Optional.of(encounter(9L, ME, 20L)));
        given(encounterPreferenceRepository.findByEncounterIdAndUserId(9L, ME))
                .willReturn(Optional.of(preference(9L, ME, true)));

        // when: 즐겨찾기 해제
        peopleService.deleteFavorite(ME, 20L);

        // then: isFavorited=false로 저장됨
        ArgumentCaptor<EncounterPreference> captor = ArgumentCaptor.forClass(EncounterPreference.class);
        then(encounterPreferenceRepository).should().save(captor.capture());
        assertThat(captor.getValue().getIsFavorited()).isFalse();
    }

    @Test
    @DisplayName("즐겨찾기 이력이 없는 상태에서 해제하면 아무것도 저장하지 않는다 (멱등)")
    void deleteFavorites_without_preference_is_noop() {
        // given: encounter는 있으나 즐겨찾기 이력이 없는 상태
        given(encounterRepository.findByUserAIdAndUserBId(ME, 20L)).willReturn(Optional.of(encounter(9L, ME, 20L)));

        // when: 즐겨찾기 해제
        peopleService.deleteFavorite(ME, 20L);

        // then: 저장하지 않음 (멱등)
        then(encounterPreferenceRepository).should(never()).save(any());
    }

    // --------------------------------------------------------- intimacySeries()

    @Test
    @DisplayName("DAY 해상도는 기간 내 관계 기록을 날짜별 포인트로 그대로 반환한다")
    void intimacySeries_day_resolution() {
        // given: 7/1, 7/3, 7/5 세 건의 관계 기록과 최신 친밀도 30
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        given(relationshipRepository.findAllByUserIdAndPartnerUserIdAndDateBetweenOrderByDateAsc(ME, 20L, from, to))
                .willReturn(List.of(
                        relationship(ME, 20L, LocalDate.of(2026, 7, 1), 10, "{}"),
                        relationship(ME, 20L, LocalDate.of(2026, 7, 3), 20, "{}"),
                        relationship(ME, 20L, LocalDate.of(2026, 7, 5), 30, "{}")));
        given(relationshipRepository.findLatestByUserIdAndPartnerUserId(ME, 20L))
                .willReturn(Optional.of(relationship(ME, 20L, LocalDate.of(2026, 7, 5), 30, "{}")));

        // when: DAY 해상도로 시계열 조회
        PeopleIntimacySeriesResult result = peopleService.intimacySeries(ME, 20L, from, to, IntimacyResolution.DAY, 10);

        // then: 기록 수만큼 포인트가 생기고 현재 친밀도는 최신 기록 값
        assertThat(result.currentIntimacy()).isEqualTo(30);
        assertThat(result.intimacySeries()).extracting(PeopleIntimacySeriesItemResult::date)
                .containsExactly(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 5));
        assertThat(result.intimacySeries()).extracting(PeopleIntimacySeriesItemResult::intimacy)
                .containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("WEEK 해상도는 from 기준 7일 버킷으로 묶고 각 버킷의 마지막 기록 값을 사용한다")
    void intimacySeries_week_resolution() {
        // given: 같은 주에 7/1(10)·7/3(20), 다음 주에 7/8(30) 기록
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        given(relationshipRepository.findAllByUserIdAndPartnerUserIdAndDateBetweenOrderByDateAsc(ME, 20L, from, to))
                .willReturn(List.of(
                        relationship(ME, 20L, LocalDate.of(2026, 7, 1), 10, "{}"),
                        relationship(ME, 20L, LocalDate.of(2026, 7, 3), 20, "{}"),
                        relationship(ME, 20L, LocalDate.of(2026, 7, 8), 30, "{}")));
        given(relationshipRepository.findLatestByUserIdAndPartnerUserId(ME, 20L))
                .willReturn(Optional.of(relationship(ME, 20L, LocalDate.of(2026, 7, 8), 30, "{}")));

        // when: WEEK 해상도로 시계열 조회
        PeopleIntimacySeriesResult result = peopleService.intimacySeries(ME, 20L, from, to, IntimacyResolution.WEEK, 10);

        // then: 버킷 시작일(7/1, 7/8) 2개 포인트로 축약되고, 친밀도는 누적 상태값이므로 구간 끝 값을 쓴다
        assertThat(result.intimacySeries()).extracting(PeopleIntimacySeriesItemResult::date)
                .containsExactly(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 8));
        assertThat(result.intimacySeries()).extracting(PeopleIntimacySeriesItemResult::intimacy)
                .containsExactly(20, 30);

        // then: 마지막 포인트가 currentIntimacy와 일치해 그래프 끝과 현재 값이 어긋나지 않는다
        assertThat(result.intimacySeries().getLast().intimacy()).isEqualTo(result.currentIntimacy());
    }

    @Test
    @DisplayName("포인트 수가 maxPoints를 넘으면 균등 간격으로 다운샘플링해 maxPoints개만 남긴다")
    void intimacySeries_downsamples_to_maxPoints() {
        // given: 5건의 일별 기록, maxPoints는 2
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        given(relationshipRepository.findAllByUserIdAndPartnerUserIdAndDateBetweenOrderByDateAsc(ME, 20L, from, to))
                .willReturn(List.of(
                        relationship(ME, 20L, LocalDate.of(2026, 7, 1), 1, "{}"),
                        relationship(ME, 20L, LocalDate.of(2026, 7, 2), 2, "{}"),
                        relationship(ME, 20L, LocalDate.of(2026, 7, 3), 3, "{}"),
                        relationship(ME, 20L, LocalDate.of(2026, 7, 4), 4, "{}"),
                        relationship(ME, 20L, LocalDate.of(2026, 7, 5), 5, "{}")));
        given(relationshipRepository.findLatestByUserIdAndPartnerUserId(ME, 20L))
                .willReturn(Optional.of(relationship(ME, 20L, LocalDate.of(2026, 7, 5), 5, "{}")));

        // when: maxPoints=2로 시계열 조회
        PeopleIntimacySeriesResult result = peopleService.intimacySeries(ME, 20L, from, to, IntimacyResolution.DAY, 2);

        // then: 각 구간의 마지막 포인트만 남아 2개가 된다
        assertThat(result.intimacySeries()).extracting(PeopleIntimacySeriesItemResult::date)
                .containsExactly(LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 5));
        assertThat(result.intimacySeries()).extracting(PeopleIntimacySeriesItemResult::intimacy)
                .containsExactly(2, 5);
    }

    @Test
    @DisplayName("관계 기록이 전혀 없으면 구간 조회·집계 전에 RELATIONSHIP_NOT_FOUND 예외가 발생한다")
    void intimacySeries_without_relationship_throws() {
        // when & then: 최신 관계 기록이 없으면 RELATIONSHIP_NOT_FOUND 예외 발생
        assertThatThrownBy(() -> peopleService.intimacySeries(
                ME, 20L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), IntimacyResolution.DAY, 10))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RELATIONSHIP_NOT_FOUND);

        // then: 버려질 구간 조회와 버킷팅을 수행하지 않고 조기 실패한다
        then(relationshipRepository).should(never())
                .findAllByUserIdAndPartnerUserIdAndDateBetweenOrderByDateAsc(any(), any(), any(), any());
    }

    // ------------------------------------------------------------------ events()

    @Test
    @DisplayName("존재하지 않는 상대의 이벤트 목록을 조회하면 USER_NOT_FOUND 예외가 발생한다")
    void events_user_not_found() {
        // when & then: 상대 유저가 없으면 USER_NOT_FOUND 예외 발생
        assertThatThrownBy(() -> peopleService.events(ME, 20L, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("lines JSON이 손상되어도 preview만 null로 떨어지고 목록 전체는 성공한다")
    void events_broken_lines_json_does_not_fail_whole_list() {
        // given: 대화 씬의 lines가 파싱 불가한 상태
        LocalDate day = LocalDate.of(2026, 7, 19);
        String brokenJson = "{\"not\":\"an array\"}";

        given(userRepository.findById(20L)).willReturn(Optional.of(user(20L, "김", "철수")));
        given(relationshipRepository.findLatestByUserIdAndPartnerUserId(ME, 20L)).willReturn(Optional.empty());
        given(sceneRepository.findDistinctDatesFromCursorByUserIdAndWithPartnerUserId(ME, 20L, null, 21))
                .willReturn(List.of(day));
        given(sceneRepository.findAllByUserIdAndWithPartnerUserIdAndDateIn(ME, 20L, List.of(day)))
                .willReturn(List.of(scene(200L, ME, day, "v1", "학교 복도", SceneType.DIALOGUE, null, null, brokenJson)));
        given(relationshipRepository.findForDeltaRange(ME, 20L, day, day)).willReturn(List.of());
        willThrow(new StreamConstraintsException("깨진 JSON"))
                .given(objectMapper).readValue(eq(brokenJson), any(TypeReference.class));

        // when: 이벤트 목록 조회
        PeopleEventsResult result = peopleService.events(ME, 20L, null, null);

        // then: 500 대신 그 날짜의 preview만 null이고 나머지는 정상 매핑된다
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().getFirst().place()).isEqualTo("학교 복도");
        assertThat(result.events().getFirst().preview()).isNull();
    }

    @Test
    @DisplayName("이벤트 목록의 첫 날짜 변화량은 페이지 구간 밖의 직전 관계 기록을 기준으로 계산된다")
    void events_delta_uses_relationship_before_page_range() {
        // given: 페이지에는 7/20만 담기지만, 직전 기록은 사흘 전인 7/17이다 (관계 기록은 날짜가 연속이 아니다)
        LocalDate before = LocalDate.of(2026, 7, 17);
        LocalDate day = LocalDate.of(2026, 7, 20);

        given(userRepository.findById(20L)).willReturn(Optional.of(user(20L, "김", "철수")));
        given(relationshipRepository.findLatestByUserIdAndPartnerUserId(ME, 20L))
                .willReturn(Optional.of(relationship(ME, 20L, day, 35, "{}")));
        given(sceneRepository.findDistinctDatesFromCursorByUserIdAndWithPartnerUserId(ME, 20L, null, 21))
                .willReturn(List.of(day));
        given(sceneRepository.findAllByUserIdAndWithPartnerUserIdAndDateIn(ME, 20L, List.of(day)))
                .willReturn(List.of(scene(100L, ME, day, "v1", "카페", SceneType.ACTION, "커피를 마셨다", "즐거웠다", null)));
        given(relationshipRepository.findForDeltaRange(ME, 20L, day, day))
                .willReturn(List.of(
                        relationship(ME, 20L, before, 10, "{}"),
                        relationship(ME, 20L, day, 35, "{}")));

        // when: 이벤트 목록 조회
        PeopleEventsResult result = peopleService.events(ME, 20L, null, null);

        // then: 구간 밖 직전 기록(10)과의 차이로 변화량이 채워진다. 구간만 조회하면 이 값이 null이 된다
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().getFirst().intimacyDelta()).isEqualTo(25);
    }

    @Test
    @DisplayName("이벤트 목록은 날짜별 첫 씬의 장소·미리보기와 전날 대비 친밀도 변화량·관계 변화를 담는다")
    void events_builds_items_with_delta_and_preview() {
        // given: 7/19(대화 씬)·7/20(행동 씬)에 기록이 있고 친밀도가 10 -> 35로 오른 상태
        LocalDate day1 = LocalDate.of(2026, 7, 19);
        LocalDate day2 = LocalDate.of(2026, 7, 20);
        String linesJson = "[{\"t\":\"narr\",\"text\":\"안녕이라고 했다\"}]";

        given(userRepository.findById(20L)).willReturn(Optional.of(user(20L, "김", "철수")));
        given(relationshipRepository.findLatestByUserIdAndPartnerUserId(ME, 20L))
                .willReturn(Optional.of(relationship(ME, 20L, day2, 45, "{}")));
        given(sceneRepository.findDistinctDatesFromCursorByUserIdAndWithPartnerUserId(ME, 20L, null, 21))
                .willReturn(List.of(day2, day1));
        given(sceneRepository.findAllByUserIdAndWithPartnerUserIdAndDateIn(ME, 20L, List.of(day2, day1)))
                .willReturn(List.of(
                        scene(100L, ME, day2, "v1", "카페", SceneType.ACTION, "커피를 마셨다", "즐거웠다", null),
                        scene(200L, ME, day1, "v1", "학교 복도", SceneType.DIALOGUE, null, null, linesJson)));
        given(relationshipRepository.findForDeltaRange(ME, 20L, day1, day2))
                .willReturn(List.of(
                        relationship(ME, 20L, day1, 10, "{}"),
                        relationship(ME, 20L, day2, 35, "{}")));
        willReturn(List.of(new SceneNarrationLine("narr", "안녕이라고 했다")))
                .given(objectMapper).readValue(eq(linesJson), any(TypeReference.class));

        // when: 이벤트 목록 조회
        PeopleEventsResult result = peopleService.events(ME, 20L, null, null);

        // then: 상대 정보와 날짜별 이벤트(변화량/관계 변화/미리보기)가 채워진다
        assertThat(result.partner().userName()).isEqualTo("김철수");
        assertThat(result.partner().intimacy()).isEqualTo(45);
        assertThat(result.partner().relationshipSpecificType()).isEqualTo(RelationshipSpecificType.RELATIONSHIP_SPECIFIC_TYPE_2);

        assertThat(result.events()).hasSize(2);
        assertThat(result.events().get(0).date()).isEqualTo(day2);
        assertThat(result.events().get(0).intimacyDelta()).isEqualTo(25);
        assertThat(result.events().get(0).relationshipChange()).isEqualTo(RelationshipSpecificType.RELATIONSHIP_SPECIFIC_TYPE_2.name());
        assertThat(result.events().get(0).place()).isEqualTo("카페");
        assertThat(result.events().get(0).preview()).isEqualTo("커피를 마셨다");

        assertThat(result.events().get(1).date()).isEqualTo(day1);
        assertThat(result.events().get(1).intimacyDelta()).isNull();
        assertThat(result.events().get(1).relationshipChange()).isNull();
        assertThat(result.events().get(1).preview()).isEqualTo("안녕이라고 했다");

        assertThat(result.page().hasMore()).isFalse();
        assertThat(result.page().nextCursor()).isNull();
    }

    // ------------------------------------------------------------------- event()

    @Test
    @DisplayName("존재하지 않는 상대의 이벤트 상세를 조회하면 USER_NOT_FOUND 예외가 발생한다")
    void event_user_not_found() {
        // when & then: 목록과 동일하게 상대 유저가 없으면 USER_NOT_FOUND 예외 발생
        assertThatThrownBy(() -> peopleService.event(ME, 20L, LocalDate.of(2026, 7, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("이벤트 상세는 상대가 참여한 씬만 쿼리로 걸러 받고 첫 씬의 version을 사용한다")
    void event_filters_scenes_by_partner() {
        given(userRepository.existsById(20L)).willReturn(true);
        // given: 상대(20)가 참여한 씬만 조인 쿼리가 돌려준다. 그날 다른 씬은 애초에 로드되지 않는다
        LocalDate date = LocalDate.of(2026, 7, 20);
        given(sceneRepository.findAllByUserIdAndWithPartnerUserIdAndDateIn(ME, 20L, List.of(date)))
                .willReturn(List.of(
                        scene(100L, ME, date, "v1", "학교 복도", SceneType.ACTION, "복도를 함께 걸었다", "설렜다", null)));
        given(scenePartnerRepository.findAllBySceneIdIn(List.of(100L)))
                .willReturn(List.of(scenePartner(100L, 20L)));
        given(userRepository.findAllById(List.of(ME, 20L)))
                .willReturn(List.of(user(ME, "나", "자신"), user(20L, "김", "철수")));

        // when: 이벤트 상세 조회
        PeopleEventResult result = peopleService.event(ME, 20L, date);

        // then: 상대가 참여한 씬만 남고 version은 그 씬의 값
        assertThat(result.date()).isEqualTo(date);
        assertThat(result.userId()).isEqualTo(20L);
        assertThat(result.version()).isEqualTo("v1");
        assertThat(result.scenes()).hasSize(1);
        assertThat(result.scenes().get(0)).isInstanceOf(PeopleEventActionSceneResult.class);

        PeopleEventActionSceneResult actionScene = (PeopleEventActionSceneResult) result.scenes().get(0);
        assertThat(actionScene.sceneId()).isEqualTo(100L);
        assertThat(actionScene.type()).isEqualTo("action");
        assertThat(actionScene.place()).isEqualTo("학교 복도");
        assertThat(actionScene.narration()).isEqualTo("복도를 함께 걸었다");
        assertThat(actionScene.mind()).isEqualTo("설렜다");
        assertThat(actionScene.with()).containsExactly(20L);
        assertThat(result.userInfos()).containsExactly(
                new PeopleEventUserInfoResult(ME, "나자신", null),
                new PeopleEventUserInfoResult(20L, "김철수", null));
    }

    @Test
    @DisplayName("이벤트 상세의 userInfos는 남은 씬의 with에 등장한 유저만 담는다")
    void event_maps_user_infos_of_remaining_scenes() {
        given(userRepository.existsById(20L)).willReturn(true);
        // given: 상대(20)가 참여한 씬에는 30도 함께 있다. 상대가 없는 씬(40만 참여)은 쿼리 단계에서 빠진다
        LocalDate date = LocalDate.of(2026, 7, 20);
        given(sceneRepository.findAllByUserIdAndWithPartnerUserIdAndDateIn(ME, 20L, List.of(date)))
                .willReturn(List.of(
                        scene(100L, ME, date, "v1", "학교 복도", SceneType.ACTION, "함께 걸었다", "설렜다", null)));
        given(scenePartnerRepository.findAllBySceneIdIn(List.of(100L)))
                .willReturn(List.of(scenePartner(100L, 20L), scenePartner(100L, 30L)));
        given(userRepository.findAllById(List.of(ME, 20L, 30L)))
                .willReturn(List.of(user(ME, "나", "자신"), user(20L, "김", "철수"), user(30L, "박", "영희")));
        given(photoRepository.findAllByUserIdInAndType(List.of(ME, 20L, 30L), PhotoType.PROFILE))
                .willReturn(List.of(Photo.create(20L, PhotoType.PROFILE, "profile/20/key", 10, 20, 100, 200, Instant.now())));
        given(cloudFrontService.getSignedUrl("profile/20/key")).willReturn("https://cdn.example.com/signed20");

        // when: 이벤트 상세 조회
        PeopleEventResult result = peopleService.event(ME, 20L, date);

        // then: 조회자 본인이 맨 앞에 오고, 쿼리에서 빠진 씬의 40은 애초에 조회되지 않으며, 사진이 없는 30은 profilePhoto가 null
        assertThat(result.userInfos()).containsExactly(
                new PeopleEventUserInfoResult(ME, "나자신", null),
                new PeopleEventUserInfoResult(20L, "김철수", new ProfilePhotoInfo("profile/20/key", "https://cdn.example.com/signed20",
                        new PhotoPosInfo(new PhotoPosInfo.StartPos(10, 20), 100, 200))),
                new PeopleEventUserInfoResult(30L, "박영희", null));
    }

    @Test
    @DisplayName("해당 날짜에 씬이 없으면 빈 씬 목록과 null version을 반환한다")
    void event_without_scenes_returns_empty() {
        given(userRepository.existsById(20L)).willReturn(true);
        given(userRepository.findAllById(List.of(ME))).willReturn(List.of(user(ME, "나", "자신")));

        // when: 씬이 없는 날짜로 이벤트 상세 조회
        PeopleEventResult result = peopleService.event(ME, 20L, LocalDate.of(2026, 7, 20));

        // then: 빈 씬 목록 + version은 null이고 userInfos에는 조회자 본인만 담긴다
        assertThat(result.scenes()).isEmpty();
        assertThat(result.version()).isNull();
        assertThat(result.userId()).isEqualTo(20L);
        assertThat(result.userInfos()).containsExactly(new PeopleEventUserInfoResult(ME, "나자신", null));
    }

    // ------------------------------------------------------------ learnedFacts()

    @Test
    @DisplayName("알게 된 사실은 최신 관계 기록의 partnerModel을 그대로 반환한다")
    void learnedFacts_returns_partner_model() {
        // given: 최신 관계 기록에 상대 모델이 저장된 상태
        given(relationshipRepository.findLatestByUserIdAndPartnerUserId(ME, 20L))
                .willReturn(Optional.of(relationship(ME, 20L, LocalDate.of(2026, 7, 20), 50, "커피를 좋아한다")));

        // when: 알게 된 사실 조회
        PeopleLearnedFactsResult result = peopleService.learnedFacts(ME, 20L);

        // then: partnerModel이 그대로 반환됨
        assertThat(result.learnedFacts()).isEqualTo("커피를 좋아한다");
    }

    @Test
    @DisplayName("관계 기록이 없는 상대의 알게 된 사실을 조회하면 RELATIONSHIP_NOT_FOUND 예외가 발생한다")
    void learnedFacts_without_relationship_throws() {
        // when & then: 최신 관계 기록이 없으면 RELATIONSHIP_NOT_FOUND 예외 발생
        assertThatThrownBy(() -> peopleService.learnedFacts(ME, 20L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RELATIONSHIP_NOT_FOUND);
    }

    // ------------------------------------------------------------------ fixtures

    private User user(Long id, String familyName, String givenName) {
        User user = User.create(
                "nick" + id, familyName, "familyHash", givenName, "givenHash",
                Gender.MALE, "aff", "affHash", "affNo", "affNoHash",
                "2000-01-01", "birthHash", "phone", "phoneHash", "email", "emailHash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Relationship relationship(Long userId, Long partnerUserId, LocalDate date, int intimacy, String partnerModel) {
        Relationship relationship = newInstance(Relationship.class);
        ReflectionTestUtils.setField(relationship, "userId", userId);
        ReflectionTestUtils.setField(relationship, "partnerUserId", partnerUserId);
        ReflectionTestUtils.setField(relationship, "date", date);
        ReflectionTestUtils.setField(relationship, "intimacy", intimacy);
        ReflectionTestUtils.setField(relationship, "partnerModel", partnerModel);
        return relationship;
    }

    private Encounter encounter(Long id, Long userId1, Long userId2) {
        Encounter encounter = Encounter.create(userId1, userId2);
        ReflectionTestUtils.setField(encounter, "id", id);
        return encounter;
    }

    private Scene scene(Long id, Long userId, LocalDate date, String version, String place,
                        SceneType type, String narration, String mind, String lines) {
        Scene scene = newInstance(Scene.class);
        ReflectionTestUtils.setField(scene, "id", id);
        ReflectionTestUtils.setField(scene, "userId", userId);
        ReflectionTestUtils.setField(scene, "date", date);
        ReflectionTestUtils.setField(scene, "version", version);
        ReflectionTestUtils.setField(scene, "place", place);
        ReflectionTestUtils.setField(scene, "startsAt", java.time.LocalTime.of(9, 0));
        ReflectionTestUtils.setField(scene, "endsAt", java.time.LocalTime.of(10, 0));
        ReflectionTestUtils.setField(scene, "type", type);
        ReflectionTestUtils.setField(scene, "narration", narration);
        ReflectionTestUtils.setField(scene, "mind", mind);
        ReflectionTestUtils.setField(scene, "lines", lines);
        return scene;
    }

    private ScenePartner scenePartner(Long sceneId, Long userId) {
        ScenePartner scenePartner = newInstance(ScenePartner.class);
        ReflectionTestUtils.setField(scenePartner, "sceneId", sceneId);
        ReflectionTestUtils.setField(scenePartner, "userId", userId);
        return scenePartner;
    }

    private Match match(Long id, Long userAId, Long userBId) {
        Match match = newInstance(Match.class);
        ReflectionTestUtils.setField(match, "id", id);
        ReflectionTestUtils.setField(match, "userAId", Math.min(userAId, userBId));
        ReflectionTestUtils.setField(match, "userBId", Math.max(userAId, userBId));
        return match;
    }

    private ChatRoom chatRoom(Long id, Long matchId) {
        ChatRoom chatRoom = ChatRoom.create(matchId);
        ReflectionTestUtils.setField(chatRoom, "id", id);
        return chatRoom;
    }

    private EncounterPreference preference(Long encounterId, Long userId, boolean isFavorited) {
        EncounterPreference preference = EncounterPreference.create(encounterId, userId);
        preference.changeIsFavorited(isFavorited);
        return preference;
    }

    private ScenePartnerRepository.SceneCountProjection sceneCount(Long partnerUserId, Long count) {
        return new ScenePartnerRepository.SceneCountProjection() {
            @Override
            public Long getPartnerUserId() {
                return partnerUserId;
            }

            @Override
            public Long getCount() {
                return count;
            }
        };
    }

    /** 엔티티가 protected 기본 생성자만 가지므로 리플렉션으로 인스턴스를 만든다. */
    private <T> T newInstance(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("테스트 엔티티 생성 실패: " + type.getName(), e);
        }
    }

    @Test
    @DisplayName("친밀도 시계열 조회 시 from이 to보다 늦으면 INVALID_DATE_RANGE 예외가 발생한다")
    void intimacySeries_when_from_after_to_throws() {
        // when & then: 뒤집힌 기간은 빈 결과 대신 명시적 오류로 거절된다
        assertThatThrownBy(() -> peopleService.intimacySeries(
                1L, 42L, LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 1), IntimacyResolution.DAY, 10))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_DATE_RANGE);
    }
}
