package com.nidus.twinly.balancegame.controller;

import com.nidus.twinly.balancegame.dto.command.BalanceGameAnswerCommand;
import com.nidus.twinly.balancegame.dto.request.BalanceGameAnswerRequest;
import com.nidus.twinly.balancegame.dto.response.BalanceGameResponse;
import com.nidus.twinly.balancegame.service.BalanceGameService;
import com.nidus.twinly.common.web.RequestId;
import com.nidus.twinly.user.dto.header.UserInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "친밀도 퀴즈")
@RestController
@RequiredArgsConstructor
public class BalanceGameController {

    private final BalanceGameService balanceGameService;

    @Operation(summary = "이번 시간 친밀도 퀴즈와 트윈과의 결과 조회")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND, RELATIONSHIP_NOT_FOUND")
    @GetMapping("/api/v1/people/{userId}/intimacy-quiz")
    public BalanceGameResponse current(@AuthenticationPrincipal UserInfo userInfo,
                                       @PathVariable("userId") String partnerUserId) {
        return BalanceGameResponse.from(balanceGameService.current(userInfo.id(), RequestId.toLong(partnerUserId, "userId")));
    }

    @Operation(summary = "트윈에게 친밀도 퀴즈 답변", description = "같은 질문이라도 트윈마다 따로 답한다")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "INVALID_REQUEST"),
            @ApiResponse(responseCode = "404", description = "INTIMACY_QUIZ_NOT_FOUND, USER_NOT_FOUND, RELATIONSHIP_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "INTIMACY_QUIZ_ALREADY_ANSWERED"),
            @ApiResponse(responseCode = "410", description = "INTIMACY_QUIZ_EXPIRED")
    })
    @PostMapping("/api/v1/intimacy-quizzes/{roundId}/answers")
    public BalanceGameResponse answer(@AuthenticationPrincipal UserInfo userInfo,
                                      @PathVariable("roundId") String roundId,
                                      @Valid @RequestBody BalanceGameAnswerRequest request) {
        return BalanceGameResponse.from(balanceGameService.answer(
                userInfo.id(), RequestId.toLong(roundId, "roundId"), BalanceGameAnswerCommand.from(request)));
    }
}
