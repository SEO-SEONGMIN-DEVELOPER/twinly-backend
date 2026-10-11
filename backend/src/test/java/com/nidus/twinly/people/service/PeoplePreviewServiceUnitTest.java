package com.nidus.twinly.people.service;

import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.crypto.CryptoProperties;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.photo.ProfileThumbnailService;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.people.dto.result.PeoplePreviewResult;
import com.nidus.twinly.people.dto.result.PeoplePreviewsResult;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.season.repository.SeasonParticipationRepository.PreviewCandidateProjection;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PhotoRepository;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PeoplePreviewServiceUnitTest {

    private static final Long ME = 1L;
    private static final Long SEASON_ID = 3L;

    @Mock
    CurrentSeasonReader currentSeasonReader;

    @Mock
    SeasonParticipationRepository seasonParticipationRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    PhotoRepository photoRepository;

    @Mock
    ProfileThumbnailService profileThumbnailService;

    @Mock
    CloudFrontService cloudFrontService;

    @Spy
    BlindIndexHasher blindIndexHasher = new BlindIndexHasher(new CryptoProperties(
            Map.of("v1", "unused"), "v1", Base64.getEncoder().encodeToString("test-hmac-key-0123456789abcdef".getBytes())));

    @InjectMocks
    PeoplePreviewService peoplePreviewService;

    @BeforeEach
    void setUp() {
        Season season = Season.create(Instant.now().minusSeconds(86400), Instant.now().plusSeconds(86400));
        ReflectionTestUtils.setField(season, "id", SEASON_ID);
        given(currentSeasonReader.read()).willReturn(season);
        given(seasonParticipationRepository.existsByUserIdAndSeasonId(ME, SEASON_ID)).willReturn(true);
        given(userRepository.findById(ME)).willReturn(Optional.of(user(ME, Gender.MALE)));
        given(photoRepository.findAllByUserIdInAndType(anyList(), eq(PhotoType.PROFILE))).willReturn(List.of());
    }

    @Test
    @DisplayName("현재 시즌 참여자가 아니면 후보를 조회하지 않고 빈 목록을 돌려준다")
    void previews_non_participant_returns_empty() {
        // given: 현재 시즌에 참여하지 않은 호출자
        given(seasonParticipationRepository.existsByUserIdAndSeasonId(ME, SEASON_ID)).willReturn(false);

        // when: 미리보기 조회
        PeoplePreviewsResult result = peoplePreviewService.previews(ME);

        // then: 빈 목록, 후보 조회 없음
        assertThat(result.previews()).isEmpty();
        then(seasonParticipationRepository).should(never()).findAllPreviewCandidates(any(), any(), any());
    }

    @Test
    @DisplayName("참여자인데 사용자 행이 없으면 USER_NOT_FOUND 예외가 발생한다")
    void previews_user_not_found() {
        // given: 참여 기록은 있지만 사용자 행이 없음
        given(userRepository.findById(ME)).willReturn(Optional.empty());

        // when & then: 미리보기 조회 시 USER_NOT_FOUND 예외
        assertThatThrownBy(() -> peoplePreviewService.previews(ME))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("후보는 현재 시즌과 오늘 KST 0시 이전 참여 조건으로 조회한다")
    void previews_queries_candidates_with_today_start() {
        // given: 후보 없음
        given(seasonParticipationRepository.findAllPreviewCandidates(anyLong(), anyLong(), any())).willReturn(List.of());

        // when: 미리보기 조회
        PeoplePreviewsResult result = peoplePreviewService.previews(ME);

        // then: 오늘 KST 0시를 Instant 로 넘기고, 후보가 없으면 빈 목록
        assertThat(result.previews()).isEmpty();
        then(seasonParticipationRepository).should().findAllPreviewCandidates(
                ME, SEASON_ID, KstTimes.toInstant(KstTimes.today().atStartOfDay()));
    }

    @Test
    @DisplayName("후보가 충분하면 이성 7명과 그 밖의 3명을 골라 최대 10명을 돌려준다")
    void previews_prefers_opposite_gender() {
        // given: 남성 호출자, 여성 후보 10명(101~110)·남성 후보 10명(201~210)
        List<PreviewCandidateProjection> candidates = new ArrayList<>();
        LongStream.rangeClosed(101, 110).forEach(id -> candidates.add(candidate(id, Gender.FEMALE)));
        LongStream.rangeClosed(201, 210).forEach(id -> candidates.add(candidate(id, Gender.MALE)));
        givenCandidates(candidates);

        // when: 미리보기 조회
        List<PeoplePreviewResult> previews = peoplePreviewService.previews(ME).previews();

        // then: 10명 중 이성(여성) 7명, 동성(남성) 3명
        assertThat(previews).hasSize(10);
        assertThat(previews).filteredOn(preview -> preview.gender() == Gender.FEMALE).hasSize(7);
        assertThat(previews).filteredOn(preview -> preview.gender() == Gender.MALE).hasSize(3);
    }

    @Test
    @DisplayName("이성 후보가 모자라면 나머지 후보로 10명을 채운다")
    void previews_fills_with_others_when_opposite_gender_is_short() {
        // given: 여성 후보 2명, 남성·성별 미입력 후보 합계 12명
        List<PreviewCandidateProjection> candidates = new ArrayList<>();
        LongStream.rangeClosed(101, 102).forEach(id -> candidates.add(candidate(id, Gender.FEMALE)));
        LongStream.rangeClosed(201, 210).forEach(id -> candidates.add(candidate(id, Gender.MALE)));
        LongStream.rangeClosed(301, 302).forEach(id -> candidates.add(candidate(id, null)));
        givenCandidates(candidates);

        // when: 미리보기 조회
        List<PeoplePreviewResult> previews = peoplePreviewService.previews(ME).previews();

        // then: 여성 2명은 모두 들어가고 나머지 8명이 채워진다
        assertThat(previews).hasSize(10);
        assertThat(previews).filteredOn(preview -> preview.gender() == Gender.FEMALE).hasSize(2);
    }

    @Test
    @DisplayName("동성·성별 미입력 후보가 모자라면 이성 후보로 10명을 채운다")
    void previews_fills_with_opposite_gender_when_others_are_short() {
        // given: 여성 후보 12명, 남성 후보 1명
        List<PreviewCandidateProjection> candidates = new ArrayList<>();
        LongStream.rangeClosed(101, 112).forEach(id -> candidates.add(candidate(id, Gender.FEMALE)));
        candidates.add(candidate(201L, Gender.MALE));
        givenCandidates(candidates);

        // when: 미리보기 조회
        List<PeoplePreviewResult> previews = peoplePreviewService.previews(ME).previews();

        // then: 동성 1명을 넣고 남은 9자리를 이성으로 채운다
        assertThat(previews).hasSize(10);
        assertThat(previews).filteredOn(preview -> preview.gender() == Gender.FEMALE).hasSize(9);
        assertThat(previews).filteredOn(preview -> preview.gender() == Gender.MALE).hasSize(1);
    }

    @Test
    @DisplayName("호출자의 성별이 없으면 성별 구분 없이 10명을 고른다")
    void previews_without_my_gender_picks_without_ratio() {
        // given: 성별 미입력 호출자, 여성 후보 15명
        given(userRepository.findById(ME)).willReturn(Optional.of(user(ME, null)));
        List<PreviewCandidateProjection> candidates = new ArrayList<>();
        LongStream.rangeClosed(101, 115).forEach(id -> candidates.add(candidate(id, Gender.FEMALE)));
        givenCandidates(candidates);

        // when: 미리보기 조회
        List<PeoplePreviewResult> previews = peoplePreviewService.previews(ME).previews();

        // then: 비율 없이 최대 인원 10명
        assertThat(previews).hasSize(10);
    }

    @Test
    @DisplayName("호출자가 여성이면 남성 후보를 이성으로 보고 7명까지 고른다")
    void previews_female_caller_prefers_male() {
        // given: 여성 호출자, 여성 후보 10명·남성 후보 10명
        given(userRepository.findById(ME)).willReturn(Optional.of(user(ME, Gender.FEMALE)));
        List<PreviewCandidateProjection> candidates = new ArrayList<>();
        LongStream.rangeClosed(101, 110).forEach(id -> candidates.add(candidate(id, Gender.FEMALE)));
        LongStream.rangeClosed(201, 210).forEach(id -> candidates.add(candidate(id, Gender.MALE)));
        givenCandidates(candidates);

        // when: 미리보기 조회
        List<PeoplePreviewResult> previews = peoplePreviewService.previews(ME).previews();

        // then: 남성 7명, 여성 3명
        assertThat(previews).filteredOn(preview -> preview.gender() == Gender.MALE).hasSize(7);
        assertThat(previews).filteredOn(preview -> preview.gender() == Gender.FEMALE).hasSize(3);
    }

    @Test
    @DisplayName("후보 조회 뒤 사용자 행이 사라진 후보는 건너뛰고 나머지만 돌려준다")
    void previews_skips_candidate_whose_user_row_disappeared() {
        // given: 후보 2명 중 102 의 사용자 행이 사용자 조회 시점에 없다
        given(seasonParticipationRepository.findAllPreviewCandidates(eq(ME), eq(SEASON_ID), any()))
                .willReturn(List.of(candidate(101L, Gender.FEMALE), candidate(102L, Gender.FEMALE)));
        given(userRepository.findAllById(anyList())).willReturn(List.of(user(101L, Gender.FEMALE)));

        // when: 미리보기 조회
        List<PeoplePreviewResult> previews = peoplePreviewService.previews(ME).previews();

        // then: 남은 1명만 내려가고 예외가 나지 않는다
        assertThat(previews).hasSize(1);
        assertThat(previews.get(0).gender()).isEqualTo(Gender.FEMALE);
    }

    @Test
    @DisplayName("같은 날 같은 호출자에게는 같은 사람을 같은 순서·같은 ref 로 돌려준다")
    void previews_are_stable_within_a_day() {
        // given: 후보 20명
        List<PreviewCandidateProjection> candidates = new ArrayList<>();
        LongStream.rangeClosed(101, 110).forEach(id -> candidates.add(candidate(id, Gender.FEMALE)));
        LongStream.rangeClosed(201, 210).forEach(id -> candidates.add(candidate(id, Gender.MALE)));
        givenCandidates(candidates);

        // when: 두 번 호출
        List<PeoplePreviewResult> first = peoplePreviewService.previews(ME).previews();
        List<PeoplePreviewResult> second = peoplePreviewService.previews(ME).previews();

        // then: 두 결과의 사람·순서·ref 가 모두 같다
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("ref 는 12자리 소문자 16진수이고 호출자마다 다르며 사용자 ID 를 담지 않는다")
    void previews_ref_differs_per_caller() {
        // given: 같은 후보 1명을 두 호출자(1, 2)가 본다
        Long other = 2L;
        given(seasonParticipationRepository.existsByUserIdAndSeasonId(other, SEASON_ID)).willReturn(true);
        given(userRepository.findById(other)).willReturn(Optional.of(user(other, Gender.MALE)));
        given(seasonParticipationRepository.findAllPreviewCandidates(anyLong(), eq(SEASON_ID), any()))
                .willReturn(List.of(candidate(101L, Gender.FEMALE)));
        given(userRepository.findAllById(List.of(101L))).willReturn(List.of(user(101L, Gender.FEMALE)));

        // when: 두 호출자가 각각 미리보기 조회
        String myRef = peoplePreviewService.previews(ME).previews().get(0).ref();
        String otherRef = peoplePreviewService.previews(other).previews().get(0).ref();

        // then: 형식은 같고 값은 호출자마다 다르며 후보 ID 가 드러나지 않는다
        assertThat(myRef).matches("[0-9a-f]{12}");
        assertThat(otherRef).matches("[0-9a-f]{12}");
        assertThat(myRef).isNotEqualTo(otherRef);
        assertThat(myRef).doesNotContain("101");
    }

    @Test
    @DisplayName("성별·학교·출생 연도는 트윈 프로필 v2 와 같은 형식으로 내려준다")
    void previews_formats_profile_fields_like_profile_v2() {
        // given: 후보 1명
        givenCandidates(List.of(candidate(101L, Gender.FEMALE)));

        // when: 미리보기 조회
        PeoplePreviewResult preview = peoplePreviewService.previews(ME).previews().get(0);

        // then: 성별 enum 그대로, 학교는 약칭(성신여대), 출생 연도는 뒤 두 자리
        User expected = user(101L, Gender.FEMALE);
        assertThat(preview.gender()).isEqualTo(Gender.FEMALE);
        assertThat(preview.organization()).isEqualTo(expected.shortOrganization());
        assertThat(preview.birthYear()).isEqualTo("02");
    }

    @Test
    @DisplayName("이미 만든 미리보기 사진이 있으면 다시 만들지 않고 그 key 의 서명 URL 을 준다")
    void previews_reuses_existing_preview_photo() {
        // given: 미리보기 key 가 저장된 사진
        givenCandidates(List.of(candidate(101L, Gender.FEMALE)));
        Photo photo = photo(11L, 101L, "profile/101/original");
        photo.changeThumbnailKey("profile/101/original-thumb");
        ReflectionTestUtils.setField(photo, "previewKey", "preview/abc");
        given(photoRepository.findAllByUserIdInAndType(List.of(101L), PhotoType.PROFILE)).willReturn(List.of(photo));
        given(cloudFrontService.getSignedUrl("preview/abc")).willReturn("https://cdn.example.com/preview/abc?sig");

        // when: 미리보기 조회
        PeoplePreviewResult preview = peoplePreviewService.previews(ME).previews().get(0);

        // then: 원본·썸네일 URL 은 만들지 않는다
        assertThat(preview.profilePhoto()).isEqualTo("https://cdn.example.com/preview/abc?sig");
        then(profileThumbnailService).should(never()).generatePreview(anyString(), any());
        then(cloudFrontService).should(never()).getSignedUrl("profile/101/original");
        then(cloudFrontService).should(never()).getSignedUrl("profile/101/original-thumb");
    }

    @Test
    @DisplayName("미리보기 사진이 없으면 원본에서 만들어 사진이 그대로일 때만 저장하고 URL 을 준다")
    void previews_creates_missing_preview_photo() {
        // given: 미리보기 key 가 없는 사진
        givenCandidates(List.of(candidate(101L, Gender.FEMALE)));
        Photo photo = photo(11L, 101L, "profile/101/original");
        given(photoRepository.findAllByUserIdInAndType(List.of(101L), PhotoType.PROFILE)).willReturn(List.of(photo));
        given(profileThumbnailService.generatePreview("profile/101/original", photo.position())).willReturn("preview/new");
        given(photoRepository.assignPreviewKey(11L, "profile/101/original", "preview/new")).willReturn(1);
        given(cloudFrontService.getSignedUrl("preview/new")).willReturn("https://cdn.example.com/preview/new?sig");

        // when: 미리보기 조회
        PeoplePreviewResult preview = peoplePreviewService.previews(ME).previews().get(0);

        // then: 새 key 의 서명 URL 을 주고, 원본 key 조건으로 저장을 위임한다
        assertThat(preview.profilePhoto()).isEqualTo("https://cdn.example.com/preview/new?sig");
        then(photoRepository).should().assignPreviewKey(11L, "profile/101/original", "preview/new");
    }

    @Test
    @DisplayName("미리보기를 만드는 사이 사진이 바뀌어 저장되지 않으면 옛 사진을 내려주지 않고 null 로 둔다")
    void previews_drops_preview_when_photo_changed_meanwhile() {
        // given: 저장 조건(원본 key 일치)이 맞지 않아 0건 갱신
        givenCandidates(List.of(candidate(101L, Gender.FEMALE)));
        Photo photo = photo(11L, 101L, "profile/101/original");
        given(photoRepository.findAllByUserIdInAndType(List.of(101L), PhotoType.PROFILE)).willReturn(List.of(photo));
        given(profileThumbnailService.generatePreview("profile/101/original", photo.position())).willReturn("preview/new");
        given(photoRepository.assignPreviewKey(11L, "profile/101/original", "preview/new")).willReturn(0);

        // when: 미리보기 조회
        PeoplePreviewResult preview = peoplePreviewService.previews(ME).previews().get(0);

        // then: 사진은 null, 서명 URL 도 만들지 않는다
        assertThat(preview.profilePhoto()).isNull();
        then(cloudFrontService).should(never()).getSignedUrl(anyString());
    }

    @Test
    @DisplayName("미리보기 사진 생성에 실패하면 그 사람의 사진만 null 로 두고 목록은 그대로 준다")
    void previews_keeps_person_when_preview_generation_fails() {
        // given: 생성 실패(null)
        givenCandidates(List.of(candidate(101L, Gender.FEMALE)));
        Photo photo = photo(11L, 101L, "profile/101/original");
        given(photoRepository.findAllByUserIdInAndType(List.of(101L), PhotoType.PROFILE)).willReturn(List.of(photo));
        given(profileThumbnailService.generatePreview("profile/101/original", photo.position())).willReturn(null);

        // when: 미리보기 조회
        List<PeoplePreviewResult> previews = peoplePreviewService.previews(ME).previews();

        // then: 사람은 남고 사진만 null, 저장은 시도하지 않는다
        assertThat(previews).hasSize(1);
        assertThat(previews.get(0).profilePhoto()).isNull();
        then(photoRepository).should(never()).assignPreviewKey(anyLong(), anyString(), anyString());
    }

    private void givenCandidates(List<PreviewCandidateProjection> candidates) {
        given(seasonParticipationRepository.findAllPreviewCandidates(eq(ME), eq(SEASON_ID), any())).willReturn(candidates);
        given(userRepository.findAllById(anyList())).willAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            return ids.stream()
                    .map(id -> user(id, candidates.stream().filter(c -> c.getUserId().equals(id)).findFirst().orElseThrow().getGender()))
                    .toList();
        });
    }

    private PreviewCandidateProjection candidate(Long userId, Gender gender) {
        return new PreviewCandidateProjection() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public Gender getGender() {
                return gender;
            }
        };
    }

    private User user(Long id, Gender gender) {
        User user = User.create(
                "nick" + id, "홍", "familyHash", "실명", "givenHash",
                gender, "성신여자대학교", "organizationHash", "aff", "affHash", "affNo", "affNoHash",
                "2002-03-04", "birthHash", "phone", "phoneHash", "email", "emailHash", null, null, null, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Photo photo(Long id, Long userId, String key) {
        Photo photo = Photo.create(userId, PhotoType.PROFILE, key, 0, 0, 100, 100, Instant.now());
        ReflectionTestUtils.setField(photo, "id", id);
        return photo;
    }
}
