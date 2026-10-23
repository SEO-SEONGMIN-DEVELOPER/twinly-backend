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
import com.nidus.twinly.legal.reader.ConsentReader;
import com.nidus.twinly.people.dto.result.PeoplePreviewResult;
import com.nidus.twinly.people.dto.result.PeoplePreviewV2Result;
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
import java.util.Set;
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
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PeoplePreviewServiceUnitTest {

    private static final Long ME = 1L;
    private static final Long SEASON_ID = 3L;
    private static final String DISCLOSURE = "thirdPartyRealIdentityDisclosure";

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

    @Mock
    ConsentReader consentReader;

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
        given(userRepository.findById(ME)).willReturn(Optional.of(user(ME, Gender.MALE)));
        given(photoRepository.findAllByUserIdInAndType(anyList(), eq(PhotoType.PROFILE))).willReturn(List.of());
    }

    @Test
    @DisplayName("호출자의 현재 시즌 참여 여부는 보지 않고 미참여자에게도 후보를 골라 돌려준다")
    void previews_ignores_caller_participation() {
        // given: 현재 시즌에 참여하지 않은 호출자, 후보 1명
        given(seasonParticipationRepository.existsByUserIdAndSeasonId(ME, SEASON_ID)).willReturn(false);
        givenCandidates(List.of(candidate(2L, Gender.FEMALE)));

        // when: 미리보기 조회
        PeoplePreviewsResult result = peoplePreviewService.previews(ME);

        // then: 참여 여부 조회 없이 후보가 그대로 내려간다
        assertThat(result.previews()).hasSize(1);
        then(seasonParticipationRepository).should(never()).existsByUserIdAndSeasonId(any(), any());
    }

    @Test
    @DisplayName("사용자 행이 없으면 USER_NOT_FOUND 예외가 발생한다")
    void previews_user_not_found() {
        // given: 사용자 행이 없음
        given(userRepository.findById(ME)).willReturn(Optional.empty());

        // when & then: 미리보기 조회 시 USER_NOT_FOUND 예외
        assertThatThrownBy(() -> peoplePreviewService.previews(ME))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("후보는 현재 시즌·오늘 KST 0시 이전 참여·실신원 제3자 제공 동의 조건으로 조회한다")
    void previews_queries_candidates_with_today_start() {
        // given: 후보 없음
        given(seasonParticipationRepository.findAllPreviewCandidates(anyLong(), anyLong(), any(), anyString())).willReturn(List.of());

        // when: 미리보기 조회
        PeoplePreviewsResult result = peoplePreviewService.previews(ME);

        // then: 오늘 KST 0시를 Instant 로 넘기고, 후보가 없으면 빈 목록
        assertThat(result.previews()).isEmpty();
        then(seasonParticipationRepository).should().findAllPreviewCandidates(
                ME, SEASON_ID, KstTimes.toInstant(KstTimes.today().atStartOfDay()), DISCLOSURE);
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
        given(seasonParticipationRepository.findAllPreviewCandidates(eq(ME), eq(SEASON_ID), any(), eq(DISCLOSURE)))
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
        given(seasonParticipationRepository.findAllPreviewCandidates(anyLong(), eq(SEASON_ID), any(), eq(DISCLOSURE)))
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

    @Test
    @DisplayName("v2: 참여자가 부르면 1.2 이상 동의자는 원본 사진·닉네임을, 그 밖의 사람은 가린 사진만 준다")
    void previewsV2_participant_unmasks_only_agreed_people() {
        // given: 참여자 호출자, 후보 101(1.2 이상 동의)·102(1.1까지만 동의) 모두 사진이 있다
        given(seasonParticipationRepository.existsByUserIdAndSeasonId(ME, SEASON_ID)).willReturn(true);
        givenCandidates(List.of(candidate(101L, Gender.FEMALE), candidate(102L, Gender.FEMALE)));
        given(consentReader.filterAgreedAtLeast(anyList(), eq(DISCLOSURE), eq("1.2"))).willReturn(Set.of(101L));
        Photo agreedPhoto = photo(11L, 101L, "profile/101/original");
        Photo maskedPhoto = photo(12L, 102L, "profile/102/original");
        ReflectionTestUtils.setField(maskedPhoto, "previewKey", "preview/102");
        given(photoRepository.findAllByUserIdInAndType(anyList(), eq(PhotoType.PROFILE))).willReturn(List.of(agreedPhoto, maskedPhoto));
        given(cloudFrontService.getSignedUrl("profile/101/original")).willReturn("https://cdn.example.com/profile/101/original?sig");
        given(cloudFrontService.getSignedUrl("preview/102")).willReturn("https://cdn.example.com/preview/102?sig");

        // when: v2 미리보기 조회
        List<PeoplePreviewV2Result> previews = peoplePreviewService.previewsV2(ME).previews();

        // then: 101 은 원본 서명 URL·position·닉네임, 102 는 가린 사진만
        assertThat(previews).hasSize(2);
        PeoplePreviewV2Result agreed = previews.stream().filter(preview -> "nick101".equals(preview.nickname())).findFirst().orElseThrow();
        assertThat(agreed.profilePhoto().photoUrl()).isEqualTo("https://cdn.example.com/profile/101/original?sig");
        assertThat(agreed.profilePhoto().position()).isEqualTo(agreedPhoto.position());
        assertThat(agreed.maskedPhotoUrl()).isNull();
        PeoplePreviewV2Result masked = previews.stream().filter(preview -> preview != agreed).findFirst().orElseThrow();
        assertThat(masked.nickname()).isNull();
        assertThat(masked.profilePhoto()).isNull();
        assertThat(masked.maskedPhotoUrl()).isEqualTo("https://cdn.example.com/preview/102?sig");
    }

    @Test
    @DisplayName("v2: 원본 사진을 주는 사람의 가린 사진은 만들지 않는다")
    void previewsV2_does_not_create_masked_photo_for_unmasked_person() {
        // given: 참여자 호출자, 1.2 이상 동의한 후보의 사진에 미리보기 key 가 아직 없다
        given(seasonParticipationRepository.existsByUserIdAndSeasonId(ME, SEASON_ID)).willReturn(true);
        givenCandidates(List.of(candidate(101L, Gender.FEMALE)));
        given(consentReader.filterAgreedAtLeast(anyList(), eq(DISCLOSURE), eq("1.2"))).willReturn(Set.of(101L));
        given(photoRepository.findAllByUserIdInAndType(anyList(), eq(PhotoType.PROFILE))).willReturn(List.of(photo(11L, 101L, "profile/101/original")));

        // when: v2 미리보기 조회
        peoplePreviewService.previewsV2(ME);

        // then: 8px 미리보기 생성·저장을 하지 않는다
        then(profileThumbnailService).should(never()).generatePreview(anyString(), any());
        then(photoRepository).should(never()).assignPreviewKey(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("v2: 미참여자가 부르면 동의 버전을 보지 않고 모두 가린 사진만 주고 닉네임은 주지 않는다")
    void previewsV2_non_participant_gets_masked_only() {
        // given: 미참여자 호출자, 사진 있는 후보 1명
        given(seasonParticipationRepository.existsByUserIdAndSeasonId(ME, SEASON_ID)).willReturn(false);
        givenCandidates(List.of(candidate(101L, Gender.FEMALE)));
        Photo photo = photo(11L, 101L, "profile/101/original");
        ReflectionTestUtils.setField(photo, "previewKey", "preview/101");
        given(photoRepository.findAllByUserIdInAndType(anyList(), eq(PhotoType.PROFILE))).willReturn(List.of(photo));
        given(cloudFrontService.getSignedUrl("preview/101")).willReturn("https://cdn.example.com/preview/101?sig");

        // when: v2 미리보기 조회
        PeoplePreviewV2Result preview = peoplePreviewService.previewsV2(ME).previews().get(0);

        // then: 가린 사진만, 동의 조회도 원본 서명도 하지 않는다
        assertThat(preview.nickname()).isNull();
        assertThat(preview.profilePhoto()).isNull();
        assertThat(preview.maskedPhotoUrl()).isEqualTo("https://cdn.example.com/preview/101?sig");
        then(consentReader).shouldHaveNoInteractions();
        then(cloudFrontService).should(never()).getSignedUrl("profile/101/original");
    }

    @Test
    @DisplayName("v2: 사진이 없는 1.2 이상 동의자는 사진 둘 다 null 이고 닉네임은 준다")
    void previewsV2_unmasked_person_without_photo() {
        // given: 참여자 호출자, 사진 없는 1.2 이상 동의 후보
        given(seasonParticipationRepository.existsByUserIdAndSeasonId(ME, SEASON_ID)).willReturn(true);
        givenCandidates(List.of(candidate(101L, Gender.FEMALE)));
        given(consentReader.filterAgreedAtLeast(anyList(), eq(DISCLOSURE), eq("1.2"))).willReturn(Set.of(101L));

        // when: v2 미리보기 조회
        PeoplePreviewV2Result preview = peoplePreviewService.previewsV2(ME).previews().get(0);

        // then: 닉네임만 있고 사진은 둘 다 null
        assertThat(preview.nickname()).isEqualTo("nick101");
        assertThat(preview.profilePhoto()).isNull();
        assertThat(preview.maskedPhotoUrl()).isNull();
    }

    @Test
    @DisplayName("v2: v1 과 같은 후보 조회를 쓰고 같은 사람·순서·ref 를 준다")
    void previewsV2_uses_same_candidates_as_v1() {
        // given: 미참여자 호출자, 후보 20명
        given(seasonParticipationRepository.existsByUserIdAndSeasonId(ME, SEASON_ID)).willReturn(false);
        List<PreviewCandidateProjection> candidates = new ArrayList<>();
        LongStream.rangeClosed(101, 110).forEach(id -> candidates.add(candidate(id, Gender.FEMALE)));
        LongStream.rangeClosed(201, 210).forEach(id -> candidates.add(candidate(id, Gender.MALE)));
        givenCandidates(candidates);

        // when: v1·v2 를 차례로 조회
        List<String> v1Refs = peoplePreviewService.previews(ME).previews().stream().map(PeoplePreviewResult::ref).toList();
        List<String> v2Refs = peoplePreviewService.previewsV2(ME).previews().stream().map(PeoplePreviewV2Result::ref).toList();

        // then: ref 목록이 순서까지 같고, 두 번 모두 같은 조건으로 후보를 조회했다
        assertThat(v2Refs).isEqualTo(v1Refs);
        then(seasonParticipationRepository).should(times(2)).findAllPreviewCandidates(
                ME, SEASON_ID, KstTimes.toInstant(KstTimes.today().atStartOfDay()), DISCLOSURE);
    }

    @Test
    @DisplayName("v2: 보여 줄 사람이 없으면 참여 여부·동의 조회 없이 빈 목록을 준다")
    void previewsV2_empty_candidates() {
        // given: 후보 없음
        givenCandidates(List.of());

        // when: v2 미리보기 조회
        List<PeoplePreviewV2Result> previews = peoplePreviewService.previewsV2(ME).previews();

        // then: 빈 목록, 이후 조회 없음
        assertThat(previews).isEmpty();
        then(seasonParticipationRepository).should(never()).existsByUserIdAndSeasonId(any(), any());
        then(consentReader).shouldHaveNoInteractions();
    }

    private void givenCandidates(List<PreviewCandidateProjection> candidates) {
        given(seasonParticipationRepository.findAllPreviewCandidates(eq(ME), eq(SEASON_ID), any(), eq(DISCLOSURE))).willReturn(candidates);
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
