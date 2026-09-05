package com.nidus.twinly.parallelrelation.integration;

import com.nidus.twinly.common.parallel.ParallelRelationType;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.parallelrelation.entity.ParallelRelation;
import com.nidus.twinly.parallelrelation.entity.ParallelRelationCode;
import com.nidus.twinly.parallelrelation.repository.ParallelRelationCodeRepository;
import com.nidus.twinly.parallelrelation.repository.ParallelRelationRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ParallelRelationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    ParallelRelationCodeRepository parallelRelationCodeRepository;

    @Autowired
    ParallelRelationRepository parallelRelationRepository;

    @Autowired
    PersonaElementRepository personaElementRepository;

    @Test
    @DisplayName("코드 발급: 실제 인증·DB까지 관통해 코드 행이 생성되고, 다시 호출해도 같은 코드가 나온다")
    void issue_code_end_to_end_and_idempotent() throws Exception {
        // given: 실제 유저 저장
        User me = saveUser();

        // when: 코드 발급 API를 두 번 호출
        String firstCode = issueCode(me);
        String secondCode = issueCode(me);

        // then: 두 번 다 같은 코드 + DB에는 행이 하나만 있고 6자리다
        assertThat(firstCode).isEqualTo(secondCode);
        assertThat(parallelRelationCodeRepository.findByUserId(me.getId())).isPresent();
        assertThat(firstCode).hasSize(6).doesNotContain("0", "O", "1", "I");
    }

    @Test
    @DisplayName("코드 제출: 유사도가 실제로 계산되어 201과 결과가 나오고 parallel_relations 행이 생성된다")
    void submit_code_end_to_end() throws Exception {
        // given: 코드 주인과 제출자 저장 + 두 사람 모두 페르소나 보유 + 주인의 코드 발급
        User codeOwner = saveUser();
        User submitter = saveUser();
        savePersona(codeOwner);
        savePersona(submitter);
        parallelRelationCodeRepository.save(ParallelRelationCode.create(codeOwner.getId(), "K7M2QX"));

        // when: 제출자가 소문자로 코드를 제출
        mockMvc.perform(post("/api/v1/parallel-relations")
                        .header("Authorization", bearer(submitter.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"k7m2qx\"}"))
                // then: 201 + 조회자가 user, 코드 주인이 partner + 이야기에 자리표시자가 남지 않는다
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.userId").value(submitter.getId().toString()))
                .andExpect(jsonPath("$.partner.userId").value(codeOwner.getId().toString()))
                .andExpect(jsonPath("$.similarity").isNumber())
                .andExpect(jsonPath("$.topPercent").isNumber())
                .andExpect(jsonPath("$.topPercent").value(allOf(greaterThan(0.0), lessThanOrEqualTo(100.0))))
                .andExpect(jsonPath("$.scoreDistribution.length()").value(18))
                .andExpect(jsonPath("$.scoreDistribution[0].from").value(10))
                .andExpect(jsonPath("$.scoreDistribution[17].to").value(99))
                .andExpect(jsonPath("$.story").isNotEmpty());

        // then: DB에 쌍이 정렬 저장되고 코드 주인이 A 역할로 남는다
        ParallelRelation saved = parallelRelationRepository.findByUserAIdAndUserBId(
                Math.min(codeOwner.getId(), submitter.getId()),
                Math.max(codeOwner.getId(), submitter.getId())).orElseThrow();
        assertThat(saved.getCodeOwnerId()).isEqualTo(codeOwner.getId());
        assertThat(saved.getSimilarity()).isBetween(0, 100);
    }

    @Test
    @DisplayName("코드 제출 재요청: 이미 결과가 있으면 201이 아니라 200이 나오고 행이 늘지 않는다")
    void submit_code_twice_returns_200() throws Exception {
        // given: 이미 한 번 제출해 결과가 만들어진 상태
        User codeOwner = saveUser();
        User submitter = saveUser();
        savePersona(codeOwner);
        savePersona(submitter);
        parallelRelationCodeRepository.save(ParallelRelationCode.create(codeOwner.getId(), "K7M2QX"));
        mockMvc.perform(post("/api/v1/parallel-relations")
                        .header("Authorization", bearer(submitter.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"K7M2QX\"}"))
                .andExpect(status().isCreated());

        // when: 같은 코드를 다시 제출
        mockMvc.perform(post("/api/v1/parallel-relations")
                        .header("Authorization", bearer(submitter.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"K7M2QX\"}"))
                // then: 200으로 기존 결과가 그대로 나온다
                .andExpect(status().isOk());

        // then: 한 쌍당 결과는 하나뿐이다
        assertThat(parallelRelationRepository.findAllByUserAIdOrUserBIdOrderByIdDesc(submitter.getId(), submitter.getId()))
                .hasSize(1);
    }

    @Test
    @DisplayName("코드 제출: 없는 코드는 404 PARALLEL_RELATION_CODE_NOT_FOUND로 매핑된다")
    void submit_unknown_code_returns_404() throws Exception {
        // given: 코드를 발급한 적 없는 유저
        User submitter = saveUser();

        // when & then: 존재하지 않는 코드를 제출하면 404와 도메인 코드가 나간다
        mockMvc.perform(post("/api/v1/parallel-relations")
                        .header("Authorization", bearer(submitter.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ZZZZZZ\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PARALLEL_RELATION_CODE_NOT_FOUND"));
    }

    @Test
    @DisplayName("목록 조회: 양방향 쿼리로 내가 A인 결과와 B인 결과가 모두 나온다")
    void relation_list_end_to_end() throws Exception {
        // given: 내가 A인 결과 한 건, B인 결과 한 건을 실제로 저장
        User me = saveUser();
        User partnerA = saveUser();
        User partnerB = saveUser();
        saveRelation(me, partnerA);
        saveRelation(partnerB, me);

        // when: 목록 조회
        mockMvc.perform(get("/api/v1/parallel-relations")
                        .header("Authorization", bearer(me.getId())))
                // then: 두 건 모두 조회되고 상대는 내가 아닌 쪽이다
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relations.length()").value(2))
                .andExpect(jsonPath("$.relations[*].partner.userId",
                        org.hamcrest.Matchers.containsInAnyOrder(partnerA.getId().toString(), partnerB.getId().toString())));
    }

    @Test
    @DisplayName("단건 조회: 당사자는 200으로 보고, 제3자는 404로 존재 여부조차 알 수 없다")
    void relation_detail_end_to_end() throws Exception {
        // given: 두 사람의 결과와 무관한 제3자
        User codeOwner = saveUser();
        User submitter = saveUser();
        User stranger = saveUser();
        ParallelRelation relation = saveRelation(codeOwner, submitter);

        // when & then: 당사자는 200으로 조회된다
        mockMvc.perform(get("/api/v1/parallel-relations/{id}", relation.getId().toString())
                        .header("Authorization", bearer(submitter.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parallelRelationId").value(relation.getId().toString()))
                .andExpect(jsonPath("$.story").isNotEmpty());

        // when & then: 제3자는 404를 받는다
        mockMvc.perform(get("/api/v1/parallel-relations/{id}", relation.getId().toString())
                        .header("Authorization", bearer(stranger.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PARALLEL_RELATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("인증 헤더가 없으면 401이고 코드 행도 생기지 않는다")
    void without_auth_returns_401() throws Exception {
        // when & then: 인증 없이 코드 발급을 시도하면 401
        mockMvc.perform(post("/api/v1/parallel-relation-codes"))
                .andExpect(status().isUnauthorized());

        // then: DB에 아무것도 저장되지 않는다
        assertThat(parallelRelationCodeRepository.count()).isZero();
    }

    private String issueCode(User user) throws Exception {
        String body = mockMvc.perform(post("/api/v1/parallel-relation-codes")
                        .header("Authorization", bearer(user.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return com.jayway.jsonpath.JsonPath.read(body, "$.code");
    }

    private void savePersona(User user) {
        personaElementRepository.saveAll(List.of(
                PersonaElement.create(user.getId(), PersonaDimension.OPENNESS, "새로운 것을 좋아한다", Instant.now()),
                PersonaElement.create(user.getId(), PersonaDimension.INTEREST, "음악", Instant.now())
        ));
    }

    @Test
    @DisplayName("단건 조회: 저장된 표시 점수가 실제 설정을 거쳐 상위 비율과 구간 분포로 관통한다")
    void relation_detail_exposes_score_fields_end_to_end() throws Exception {
        // given: 표시 점수 78, close 등급으로 결과 한 건을 실제 저장
        User codeOwner = saveUser();
        User submitter = saveUser();
        ParallelRelation relation = saveRelation(codeOwner, submitter, 78, ParallelRelationType.CLOSE);

        // when: 당사자가 단건 조회
        mockMvc.perform(get("/api/v1/parallel-relations/{id}", relation.getId().toString())
                        .header("Authorization", bearer(submitter.getId())))
                // then: 목킹 없이 실제 분위수 곡선이 계산한 값이 그대로 실린다
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.similarity").value(78))
                .andExpect(jsonPath("$.topPercent").value(27.4))
                .andExpect(jsonPath("$.relation").value("close"))
                // then: 구간 분포는 10부터 99까지 5점 단위 18구간으로 빈틈없이 이어진다
                .andExpect(jsonPath("$.scoreDistribution.length()").value(18))
                .andExpect(jsonPath("$.scoreDistribution[0].from").value(10))
                .andExpect(jsonPath("$.scoreDistribution[0].to").value(14))
                .andExpect(jsonPath("$.scoreDistribution[17].from").value(95))
                .andExpect(jsonPath("$.scoreDistribution[17].to").value(99))
                // then: 봉우리 구간의 확률까지 설정대로 나온다
                .andExpect(jsonPath("$.scoreDistribution[13].from").value(75))
                .andExpect(jsonPath("$.scoreDistribution[13].percent").value(17.3));
    }

    @Test
    @DisplayName("목록 조회: 항목마다 상위 비율은 실리고 구간 분포는 실리지 않는다")
    void relation_list_carries_top_percent_without_distribution() throws Exception {
        // given: 표시 점수 78로 결과 한 건을 실제 저장
        User me = saveUser();
        User partner = saveUser();
        saveRelation(partner, me, 78, ParallelRelationType.CLOSE);

        // when: 목록 조회
        mockMvc.perform(get("/api/v1/parallel-relations")
                        .header("Authorization", bearer(me.getId())))
                // then: 단건과 같은 상위 비율이 나오지만 구간 분포는 목록에서 빠진다
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relations[0].similarity").value(78))
                .andExpect(jsonPath("$.relations[0].topPercent").value(27.4))
                .andExpect(jsonPath("$.relations[0].scoreDistribution").doesNotExist());
    }

    private ParallelRelation saveRelation(User codeOwner, User submitter, int similarity, ParallelRelationType relation) {
        return parallelRelationRepository.save(ParallelRelation.create(
                codeOwner.getId(), submitter.getId(), similarity, similarity / 100.0, relation, 3));
    }

    private ParallelRelation saveRelation(User codeOwner, User submitter) {
        return parallelRelationRepository.save(ParallelRelation.create(
                codeOwner.getId(), submitter.getId(), 94, 0.784,
                ParallelRelationType.BEST_FRIEND, 3));
    }
}
