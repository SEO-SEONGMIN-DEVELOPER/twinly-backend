package com.nidus.twinly.parallelrelation.service;

import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.parallel.ParallelRelationResolver;
import com.nidus.twinly.common.parallel.ParallelRelationResult;
import com.nidus.twinly.common.parallel.ParallelRelationType;
import com.nidus.twinly.common.persona.PersonaSimilarity;
import com.nidus.twinly.common.persona.PersonaSimilarityCalculator;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.parallelrelation.dto.command.ParallelRelationSubmitCodeCommand;
import com.nidus.twinly.parallelrelation.dto.result.ParallelRelationListResult;
import com.nidus.twinly.parallelrelation.dto.result.ParallelRelationSubmitCodeResult;
import com.nidus.twinly.parallelrelation.entity.ParallelRelation;
import com.nidus.twinly.parallelrelation.entity.ParallelRelationCode;
import com.nidus.twinly.parallelrelation.repository.ParallelRelationCodeRepository;
import com.nidus.twinly.parallelrelation.repository.ParallelRelationRepository;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PersonaElementRepository;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ParallelRelationServiceUnitTest {

    private static final Long CODE_OWNER_ID = 12L;
    private static final Long SUBMITTER_ID = 77L;
    private static final String CODE = "K7M2QX";

    @Mock
    ParallelRelationCodeRepository parallelRelationCodeRepository;

    @Mock
    ParallelRelationRepository parallelRelationRepository;

    @Mock
    ParallelRelationCodeIssuer parallelRelationCodeIssuer;

    @Mock
    ParallelRelationResolver parallelRelationResolver;

    @Mock
    PersonaSimilarityCalculator personaSimilarityCalculator;

    @Mock
    PersonaElementRepository personaElementRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    PhotoRepository photoRepository;

    @Mock
    CloudFrontService cloudFrontService;

    @InjectMocks
    ParallelRelationService parallelRelationService;

    @Test
    @DisplayName("이미 코드가 있으면 새로 발급하지 않고 그 코드를 그대로 돌려준다 (멱등)")
    void issue_code_is_idempotent() {
        // given: 이미 발급된 코드가 있다
        given(parallelRelationCodeRepository.findByUserId(CODE_OWNER_ID))
                .willReturn(Optional.of(ParallelRelationCode.create(CODE_OWNER_ID, CODE)));

        // when: 코드 발급을 다시 요청
        var result = parallelRelationService.issueCode(CODE_OWNER_ID);

        // then: 같은 코드 반환 + 새 코드 발급·저장 없음
        assertThat(result.code()).isEqualTo(CODE);
        assertThat(result.shareMessage()).contains(CODE);
        then(parallelRelationCodeIssuer).should(never()).issue();
        then(parallelRelationCodeRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("코드가 없으면 발급기에서 받은 코드로 새로 저장한다")
    void issue_code_saves_new_code() {
        // given: 발급 이력이 없고 발급기가 새 코드를 준다
        given(parallelRelationCodeRepository.findByUserId(CODE_OWNER_ID)).willReturn(Optional.empty());
        given(parallelRelationCodeIssuer.issue()).willReturn(CODE);
        given(parallelRelationCodeRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        // when: 코드 발급
        var result = parallelRelationService.issueCode(CODE_OWNER_ID);

        // then: 발급받은 코드로 저장 + 그 코드 반환
        ArgumentCaptor<ParallelRelationCode> captor = ArgumentCaptor.forClass(ParallelRelationCode.class);
        then(parallelRelationCodeRepository).should().save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(CODE_OWNER_ID);
        assertThat(captor.getValue().getCode()).isEqualTo(CODE);
        assertThat(result.code()).isEqualTo(CODE);
    }

    @Test
    @DisplayName("없는 코드를 제출하면 PARALLEL_RELATION_CODE_NOT_FOUND 예외가 발생한다")
    void submit_code_with_unknown_code_throws() {
        // given: 해당 코드가 없다
        given(parallelRelationCodeRepository.findByCode(CODE)).willReturn(Optional.empty());

        // when & then: 코드 없음 예외 + 결과 저장 안 함
        assertThatThrownBy(() -> parallelRelationService.submitCode(SUBMITTER_ID, new ParallelRelationSubmitCodeCommand(CODE)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PARALLEL_RELATION_CODE_NOT_FOUND);

        then(parallelRelationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("소문자로 제출해도 대문자로 정규화해 코드를 찾는다")
    void submit_code_normalizes_to_upper_case() {
        // given: 소문자 코드로 제출하고, 조회는 실패시켜 흐름을 끊는다
        given(parallelRelationCodeRepository.findByCode(CODE)).willReturn(Optional.empty());

        // when: 소문자 코드로 제출
        assertThatThrownBy(() -> parallelRelationService.submitCode(SUBMITTER_ID, new ParallelRelationSubmitCodeCommand("k7m2qx")))
                .isInstanceOf(BusinessException.class);

        // then: 대문자로 바꾼 코드로 조회했다
        then(parallelRelationCodeRepository).should().findByCode(CODE);
    }

    @Test
    @DisplayName("내가 발급한 코드를 제출하면 OWN_PARALLEL_RELATION_CODE 예외가 발생한다")
    void submit_own_code_throws() {
        // given: 코드 주인이 제출자 본인이다
        given(parallelRelationCodeRepository.findByCode(CODE))
                .willReturn(Optional.of(ParallelRelationCode.create(CODE_OWNER_ID, CODE)));

        // when & then: 본인 코드 예외 + 결과 저장 안 함
        assertThatThrownBy(() -> parallelRelationService.submitCode(CODE_OWNER_ID, new ParallelRelationSubmitCodeCommand(CODE)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OWN_PARALLEL_RELATION_CODE);

        then(parallelRelationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("이미 그 친구와의 결과가 있으면 새로 만들지 않고 created=false로 기존 결과를 돌려준다")
    void submit_code_returns_existing_relation() {
        // given: 두 사람의 결과가 이미 저장되어 있다
        givenCodeOwner();
        ParallelRelation existingRelation = savedRelation(1041L);
        given(parallelRelationRepository.findByUserAIdAndUserBId(CODE_OWNER_ID, SUBMITTER_ID))
                .willReturn(Optional.of(existingRelation));
        givenUsersAndStory();

        // when: 같은 코드를 다시 제출
        ParallelRelationSubmitCodeResult result =
                parallelRelationService.submitCode(SUBMITTER_ID, new ParallelRelationSubmitCodeCommand(CODE));

        // then: created=false + 기존 결과 반환 + 유사도 재계산·저장 없음
        assertThat(result.created()).isFalse();
        assertThat(result.relation().parallelRelationId()).isEqualTo(1041L);
        then(parallelRelationRepository).should(never()).save(any());
        then(personaSimilarityCalculator).should(never()).similarity(any(), any());
    }

    @Test
    @DisplayName("코드 주인이 탈퇴했으면 USER_NOT_FOUND 예외가 발생한다")
    void submit_code_of_withdrawn_owner_throws() {
        // given: 코드는 살아 있지만 주인이 탈퇴한 상태다
        givenCodeOwner();
        given(parallelRelationRepository.findByUserAIdAndUserBId(CODE_OWNER_ID, SUBMITTER_ID)).willReturn(Optional.empty());
        User withdrawnOwner = user(CODE_OWNER_ID, "김", "지훈");
        withdrawnOwner.delete();
        given(userRepository.findById(CODE_OWNER_ID)).willReturn(Optional.of(withdrawnOwner));

        // when & then: 탈퇴 유저 예외 + 결과 저장 안 함
        assertThatThrownBy(() -> parallelRelationService.submitCode(SUBMITTER_ID, new ParallelRelationSubmitCodeCommand(CODE)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        then(parallelRelationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("상대의 페르소나가 없으면 PERSONA_NOT_FOUND 예외가 발생한다")
    void submit_code_without_persona_throws() {
        // given: 코드 주인은 살아 있으나 페르소나가 없다
        givenCodeOwner();
        given(parallelRelationRepository.findByUserAIdAndUserBId(CODE_OWNER_ID, SUBMITTER_ID)).willReturn(Optional.empty());
        given(userRepository.findById(CODE_OWNER_ID)).willReturn(Optional.of(user(CODE_OWNER_ID, "김", "지훈")));
        given(personaElementRepository.existsByUserId(CODE_OWNER_ID)).willReturn(false);

        // when & then: 페르소나 없음 예외 + 결과 저장 안 함
        assertThatThrownBy(() -> parallelRelationService.submitCode(SUBMITTER_ID, new ParallelRelationSubmitCodeCommand(CODE)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PERSONA_NOT_FOUND);

        then(parallelRelationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("결과가 없으면 유사도를 백분율로 환산하고 코드 주인을 A로 고정해 저장한다")
    void submit_code_creates_relation() {
        // given: 결과가 없고 두 사람 모두 페르소나가 있으며 유사도가 0.78이다
        givenCodeOwner();
        given(parallelRelationRepository.findByUserAIdAndUserBId(CODE_OWNER_ID, SUBMITTER_ID)).willReturn(Optional.empty());
        given(userRepository.findById(CODE_OWNER_ID)).willReturn(Optional.of(user(CODE_OWNER_ID, "김", "지훈")));
        given(personaElementRepository.existsByUserId(anyLong())).willReturn(true);
        given(personaElementRepository.findAllByUserIdOrderByIdAsc(anyLong())).willReturn(List.of());
        given(personaSimilarityCalculator.similarity(any(), any())).willReturn(new PersonaSimilarity(0.784, Map.of()));
        given(parallelRelationResolver.relationOf(0.784)).willReturn(ParallelRelationType.BEST_FRIEND);
        given(parallelRelationResolver.pickStoryIndex(ParallelRelationType.BEST_FRIEND)).willReturn(7);
        given(parallelRelationRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        givenUsersAndStory();

        // when: 친구 코드를 제출
        ParallelRelationSubmitCodeResult result =
                parallelRelationService.submitCode(SUBMITTER_ID, new ParallelRelationSubmitCodeCommand(CODE));

        // then: created=true + 코드 주인이 A(codeOwnerId) + 유사도 78 + 이야기 번호 7로 저장
        ArgumentCaptor<ParallelRelation> captor = ArgumentCaptor.forClass(ParallelRelation.class);
        then(parallelRelationRepository).should().save(captor.capture());
        assertThat(captor.getValue().getCodeOwnerId()).isEqualTo(CODE_OWNER_ID);
        assertThat(captor.getValue().getUserAId()).isEqualTo(CODE_OWNER_ID);
        assertThat(captor.getValue().getUserBId()).isEqualTo(SUBMITTER_ID);
        assertThat(captor.getValue().getSimilarity()).isEqualTo(78);
        assertThat(captor.getValue().getStoryIndex()).isEqualTo(7);
        assertThat(result.created()).isTrue();
    }

    @Test
    @DisplayName("목록 조회 시 탈퇴한 상대와의 결과는 빠진다")
    void relation_list_excludes_withdrawn_partner() {
        // given: 결과 두 건 중 한 건의 상대가 탈퇴했다
        ParallelRelation aliveRelation = savedRelation(1041L);
        ParallelRelation withdrawnRelation = relation(2L, 99L, 1042L);
        given(parallelRelationRepository.findAllByUserAIdOrUserBIdOrderByIdDesc(SUBMITTER_ID, SUBMITTER_ID))
                .willReturn(List.of(aliveRelation, withdrawnRelation));

        User withdrawnPartner = user(2L, "박", "민수");
        withdrawnPartner.delete();
        given(userRepository.findAllById(anyList()))
                .willReturn(List.of(user(CODE_OWNER_ID, "김", "지훈"), withdrawnPartner));
        given(photoRepository.findAllByUserIdInAndType(anyList(), any())).willReturn(List.of());
        given(parallelRelationResolver.title(any(), anyInt())).willReturn("아무때나 전화해도 좋아하는 사이");

        // when: 목록 조회
        ParallelRelationListResult result = parallelRelationService.relationList(SUBMITTER_ID);

        // then: 살아 있는 상대와의 결과 한 건만 남는다
        assertThat(result.relations()).hasSize(1);
        assertThat(result.relations().get(0).partner().userId()).isEqualTo(CODE_OWNER_ID);
    }

    @Test
    @DisplayName("결과가 없으면 빈 목록을 돌려주고 유저·사진을 조회하지 않는다")
    void relation_list_returns_empty() {
        // given: 참여한 결과가 없다
        given(parallelRelationRepository.findAllByUserAIdOrUserBIdOrderByIdDesc(SUBMITTER_ID, SUBMITTER_ID))
                .willReturn(List.of());

        // when: 목록 조회
        ParallelRelationListResult result = parallelRelationService.relationList(SUBMITTER_ID);

        // then: 빈 배열 + 불필요한 조회 없음
        assertThat(result.relations()).isEmpty();
        then(userRepository).should(never()).findAllById(anyList());
        then(photoRepository).should(never()).findAllByUserIdInAndType(anyList(), any());
    }

    @Test
    @DisplayName("당사자가 아닌 결과를 조회하면 PARALLEL_RELATION_NOT_FOUND 예외가 발생한다")
    void relation_detail_of_other_users_throws() {
        // given: 나와 무관한 두 사람의 결과다
        given(parallelRelationRepository.findById(1041L)).willReturn(Optional.of(savedRelation(1041L)));

        // when & then: 없는 결과와 같은 예외로 처리된다
        assertThatThrownBy(() -> parallelRelationService.relationDetail(999L, 1041L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PARALLEL_RELATION_NOT_FOUND);
    }

    @Test
    @DisplayName("상대가 탈퇴한 결과를 조회하면 PARALLEL_RELATION_NOT_FOUND 예외가 발생한다")
    void relation_detail_with_withdrawn_partner_throws() {
        // given: 당사자이지만 상대가 탈퇴했다
        given(parallelRelationRepository.findById(1041L)).willReturn(Optional.of(savedRelation(1041L)));
        User withdrawnPartner = user(CODE_OWNER_ID, "김", "지훈");
        withdrawnPartner.delete();
        given(userRepository.findById(CODE_OWNER_ID)).willReturn(Optional.of(withdrawnPartner));

        // when & then: 없는 결과와 같은 예외로 처리된다
        assertThatThrownBy(() -> parallelRelationService.relationDetail(SUBMITTER_ID, 1041L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PARALLEL_RELATION_NOT_FOUND);
    }

    @Test
    @DisplayName("단건 조회 시 이야기는 코드 주인을 A로 두고 렌더링된다")
    void relation_detail_renders_story_with_code_owner_as_a() {
        // given: 당사자가 조회하고, 두 사람 모두 정상 상태다
        given(parallelRelationRepository.findById(1041L)).willReturn(Optional.of(savedRelation(1041L)));
        given(userRepository.findById(CODE_OWNER_ID)).willReturn(Optional.of(user(CODE_OWNER_ID, "김", "지훈")));
        givenUsersAndStory();

        // when: 제출자가 단건 조회
        var result = parallelRelationService.relationDetail(SUBMITTER_ID, 1041L);

        // then: 조회자가 user, 상대가 partner + 렌더링은 코드 주인 이름이 앞에 온다
        assertThat(result.user().userId()).isEqualTo(SUBMITTER_ID);
        assertThat(result.partner().userId()).isEqualTo(CODE_OWNER_ID);
        then(parallelRelationResolver).should().render(ParallelRelationType.BEST_FRIEND, 7, "지훈", "서연");
    }

    private void givenCodeOwner() {
        given(parallelRelationCodeRepository.findByCode(CODE))
                .willReturn(Optional.of(ParallelRelationCode.create(CODE_OWNER_ID, CODE)));
    }

    private void givenUsersAndStory() {
        given(userRepository.findAllById(anyList()))
                .willReturn(List.of(user(CODE_OWNER_ID, "김", "지훈"), user(SUBMITTER_ID, "이", "서연")));
        given(photoRepository.findAllByUserIdInAndType(anyList(), any())).willReturn(List.of());
        given(parallelRelationResolver.render(any(), anyInt(), anyString(), anyString()))
                .willReturn(new ParallelRelationResult(ParallelRelationType.BEST_FRIEND, "제목", "이야기"));
    }

    private ParallelRelation savedRelation(Long id) {
        return relation(CODE_OWNER_ID, SUBMITTER_ID, id);
    }

    private ParallelRelation relation(Long codeOwnerId, Long submitterId, Long id) {
        ParallelRelation relation = ParallelRelation.create(codeOwnerId, submitterId, 78, ParallelRelationType.BEST_FRIEND, 7);
        ReflectionTestUtils.setField(relation, "id", id);
        ReflectionTestUtils.setField(relation, "createdAt", Instant.parse("2026-08-18T03:11:22Z"));

        return relation;
    }

    private User user(Long id, String familyName, String givenName) {
        User user = User.create(
                "nick" + id,
                familyName, "familyHash",
                givenName, "givenHash",
                Gender.MALE,
                "한국대학교", "orgHash",
                "컴퓨터공학과", "affHash",
                "20", "affNoHash",
                "2000-01-01", "birthHash",
                "phone" + id, "phoneHash",
                "email" + id + "@test.com", "emailHash"
        );
        ReflectionTestUtils.setField(user, "id", id);

        return user;
    }
}
