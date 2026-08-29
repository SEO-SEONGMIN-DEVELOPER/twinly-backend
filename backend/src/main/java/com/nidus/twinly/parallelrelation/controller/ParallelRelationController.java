package com.nidus.twinly.parallelrelation.controller;

import com.nidus.twinly.common.web.RequestId;
import com.nidus.twinly.parallelrelation.dto.command.ParallelRelationSubmitCodeCommand;
import com.nidus.twinly.parallelrelation.dto.request.ParallelRelationSubmitCodeRequest;
import com.nidus.twinly.parallelrelation.dto.response.ParallelRelationDetailResponse;
import com.nidus.twinly.parallelrelation.dto.response.ParallelRelationIssueCodeResponse;
import com.nidus.twinly.parallelrelation.dto.response.ParallelRelationListResponse;
import com.nidus.twinly.parallelrelation.dto.result.ParallelRelationSubmitCodeResult;
import com.nidus.twinly.parallelrelation.service.ParallelRelationService;
import com.nidus.twinly.user.dto.header.UserInfo;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "평행우주 관계")
@RestController
@RequiredArgsConstructor
public class ParallelRelationController {

    private final ParallelRelationService parallelRelationService;

    @PostMapping("/api/v1/parallel-relation-codes")
    public ParallelRelationIssueCodeResponse issueCode(@AuthenticationPrincipal UserInfo userInfo) {
        return ParallelRelationIssueCodeResponse.from(parallelRelationService.issueCode(userInfo.id()));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "CREATED"),
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "PARALLEL_RELATION_CODE_NOT_FOUND, USER_NOT_FOUND"),
            @ApiResponse(responseCode = "422", description = "OWN_PARALLEL_RELATION_CODE, PERSONA_NOT_FOUND")
    })
    @PostMapping("/api/v1/parallel-relations")
    public ResponseEntity<ParallelRelationDetailResponse> submitCode(@AuthenticationPrincipal UserInfo userInfo,
                                                                     @Valid @RequestBody ParallelRelationSubmitCodeRequest request) {
        ParallelRelationSubmitCodeResult result = parallelRelationService.submitCode(userInfo.id(), ParallelRelationSubmitCodeCommand.from(request));

        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ParallelRelationDetailResponse.from(result.relation()));
    }

    @GetMapping("/api/v1/parallel-relations")
    public ParallelRelationListResponse relationList(@AuthenticationPrincipal UserInfo userInfo) {
        return ParallelRelationListResponse.from(parallelRelationService.relationList(userInfo.id()));
    }

    @ApiResponse(responseCode = "404", description = "PARALLEL_RELATION_NOT_FOUND")
    @GetMapping("/api/v1/parallel-relations/{parallelRelationId}")
    public ParallelRelationDetailResponse relationDetail(@AuthenticationPrincipal UserInfo userInfo,
                                                         @PathVariable String parallelRelationId) {
        return ParallelRelationDetailResponse.from(parallelRelationService.relationDetail(userInfo.id(), RequestId.toLong(parallelRelationId, "parallelRelationId")));
    }
}
