package com.nidus.twinly.parallelrelation.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.common.parallel.ParallelRelationType;
import com.nidus.twinly.common.parallel.ParallelScoreBand;
import com.nidus.twinly.common.security.SecurityConfig;
import com.nidus.twinly.parallelrelation.dto.command.ParallelRelationSubmitCodeCommand;
import com.nidus.twinly.parallelrelation.dto.result.ParallelRelationDetailResult;
import com.nidus.twinly.parallelrelation.dto.result.ParallelRelationIssueCodeResult;
import com.nidus.twinly.parallelrelation.dto.result.ParallelRelationListItemResult;
import com.nidus.twinly.parallelrelation.dto.result.ParallelRelationListResult;
import com.nidus.twinly.parallelrelation.dto.result.ParallelRelationSubmitCodeResult;
import com.nidus.twinly.parallelrelation.dto.result.ParallelRelationUserResult;
import com.nidus.twinly.parallelrelation.service.ParallelRelationService;
import com.nidus.twinly.user.dto.header.UserInfo;
import com.nidus.twinly.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ParallelRelationController.class)
@Import(SecurityConfig.class)
class ParallelRelationControllerUnitTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ParallelRelationService parallelRelationService;

    // SecurityConfig가 JWT·익명 세션 필터를 함께 만들고 각 필터가 이 서비스에 의존하므로 슬라이스 기동에 둘 다 필수.
    @MockitoBean
    UserService userService;

    @MockitoBean
    AnonService anonService;

    @BeforeEach
    void setUp() {
        given(userService.resolveByAccessToken(anyString()))
                .willReturn(new UserInfo(12L));
    }

    @Test
    @DisplayName("코드 발급 성공 시 200과 코드·공유문구를 반환하고 인증 유저 id로 서비스를 호출한다")
    void issue_code_success() throws Exception {
        // given: 서비스가 발급된 코드를 돌려준다
        given(parallelRelationService.issueCode(12L))
                .willReturn(new ParallelRelationIssueCodeResult("K7M2QX", "코드: K7M2QX"));

        // when: 인증 상태로 코드 발급 API 호출
        var result = mockMvc.perform(post("/api/v1/parallel-relation-codes")
                .header("Authorization", "Bearer access-token"));

        // then: 200 + 코드·공유문구 JSON 반환
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("K7M2QX"))
                .andExpect(jsonPath("$.shareMessage").value("코드: K7M2QX"));
        then(parallelRelationService).should().issueCode(12L);
    }

    @Test
    @DisplayName("코드 발급 시 인증 헤더가 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void issue_code_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 코드 발급 API 호출
        var result = mockMvc.perform(post("/api/v1/parallel-relation-codes"));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(parallelRelationService).should(never()).issueCode(anyLong());
    }

    @Test
    @DisplayName("코드 제출로 결과가 새로 생성되면 201과 결과 전체를 반환한다")
    void submit_code_created_returns_201() throws Exception {
        // given: 서비스가 새로 생성된 결과를 돌려준다
        given(parallelRelationService.submitCode(eq(12L), any(ParallelRelationSubmitCodeCommand.class)))
                .willReturn(new ParallelRelationSubmitCodeResult(true, detailResult()));

        // when: 친구 코드를 제출
        var result = mockMvc.perform(post("/api/v1/parallel-relations")
                .header("Authorization", "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"K7M2QX\"}"));

        // then: 201 + 숫자 id가 문자열로 직렬화된 결과 JSON
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.parallelRelationId").value("1041"))
                .andExpect(jsonPath("$.user.userId").value("12"))
                .andExpect(jsonPath("$.partner.userId").value("77"))
                .andExpect(jsonPath("$.relation").value("bestFriend"))
                .andExpect(jsonPath("$.similarity").value(78));
    }

    @Test
    @DisplayName("이미 있는 결과를 돌려받으면 201이 아니라 200을 반환한다")
    void submit_code_existing_returns_200() throws Exception {
        // given: 서비스가 기존 결과를 돌려준다(created=false)
        given(parallelRelationService.submitCode(eq(12L), any(ParallelRelationSubmitCodeCommand.class)))
                .willReturn(new ParallelRelationSubmitCodeResult(false, detailResult()));

        // when: 같은 코드를 다시 제출
        var result = mockMvc.perform(post("/api/v1/parallel-relations")
                .header("Authorization", "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"K7M2QX\"}"));

        // then: 200 + 같은 결과 JSON
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.parallelRelationId").value("1041"));
    }

    @Test
    @DisplayName("코드가 비어 있으면 400을 반환하고 서비스를 호출하지 않는다")
    void submit_code_with_blank_code_returns_400() throws Exception {
        // when: code가 공백인 요청 본문으로 제출
        var result = mockMvc.perform(post("/api/v1/parallel-relations")
                .header("Authorization", "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"  \"}"));

        // then: @NotBlank 위반으로 400 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(parallelRelationService).should(never()).submitCode(anyLong(), any());
    }

    @Test
    @DisplayName("목록 조회 성공 시 200과 상대·관계 정보를 반환한다")
    void relation_list_success() throws Exception {
        // given: 서비스가 결과 한 건을 돌려준다
        given(parallelRelationService.relationList(12L))
                .willReturn(new ParallelRelationListResult(List.of(new ParallelRelationListItemResult(
                        1041L,
                        new ParallelRelationUserResult(77L, "서연", null),
                        ParallelRelationType.BEST_FRIEND,
                        "아무때나 전화해도 좋아하는 사이",
                        78,
                        27.4,
                        Instant.parse("2026-08-18T03:11:22Z")
                ))));

        // when: 목록 API 호출
        var result = mockMvc.perform(get("/api/v1/parallel-relations")
                .header("Authorization", "Bearer access-token"));

        // then: 200 + 상대 정보와 관계 등급이 담긴 배열
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.relations[0].parallelRelationId").value("1041"))
                .andExpect(jsonPath("$.relations[0].partner.userId").value("77"))
                .andExpect(jsonPath("$.relations[0].partner.profilePhoto").doesNotExist())
                .andExpect(jsonPath("$.relations[0].relation").value("bestFriend"))
                .andExpect(jsonPath("$.relations[0].similarity").value(78))
                .andExpect(jsonPath("$.relations[0].topPercent").value(27.4));
    }

    @Test
    @DisplayName("단건 조회 성공 시 200과 이야기까지 담긴 결과를 반환한다")
    void relation_detail_success() throws Exception {
        // given: 서비스가 결과 단건을 돌려준다
        given(parallelRelationService.relationDetail(12L, 1041L)).willReturn(detailResult());

        // when: 단건 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/parallel-relations/{parallelRelationId}", "1041")
                .header("Authorization", "Bearer access-token"));

        // then: 200 + 이야기 문장 포함 + 인증 유저 id와 경로 id로 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.story").value("지훈과 서연은 다른 평행우주에서는"))
                .andExpect(jsonPath("$.similarity").value(78))
                .andExpect(jsonPath("$.topPercent").value(27.4))
                .andExpect(jsonPath("$.scoreDistribution.length()").value(2))
                .andExpect(jsonPath("$.scoreDistribution[1].from").value(75))
                .andExpect(jsonPath("$.scoreDistribution[1].to").value(79))
                .andExpect(jsonPath("$.scoreDistribution[1].percent").value(17.3));
        then(parallelRelationService).should().relationDetail(12L, 1041L);
    }

    @Test
    @DisplayName("단건 조회의 경로 변수가 숫자가 아니면 400을 반환하고 서비스를 호출하지 않는다")
    void relation_detail_with_non_numeric_id_returns_400() throws Exception {
        // when: 경로 변수를 숫자가 아닌 값으로 단건 조회
        var result = mockMvc.perform(get("/api/v1/parallel-relations/{parallelRelationId}", "abc")
                .header("Authorization", "Bearer access-token"));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(parallelRelationService).should(never()).relationDetail(anyLong(), anyLong());
    }

    private ParallelRelationDetailResult detailResult() {
        return new ParallelRelationDetailResult(
                1041L,
                new ParallelRelationUserResult(12L, "지훈", null),
                new ParallelRelationUserResult(77L, "서연", null),
                78,
                27.4,
                ParallelRelationType.BEST_FRIEND,
                "아무때나 전화해도 좋아하는 사이",
                "지훈과 서연은 다른 평행우주에서는",
                List.of(new ParallelScoreBand(70, 74, 11.6), new ParallelScoreBand(75, 79, 17.3)),
                Instant.parse("2026-08-18T03:11:22Z")
        );
    }
}
