package com.nidus.twinly.admin.dto.response;

import com.nidus.twinly.admin.dto.result.AdminSurveyResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record AdminSurveyResponse(
        @Schema(description = "응답률의 분모가 되는 가입자 수. 이 통계는 탈퇴·탈퇴 유예 회원도 센다")
        AdminGenderCountResponse members,
        @Schema(description = "현재 앱에 나가는 성향 설문 문항. 빠진 옛 문항의 응답은 세지 않는다")
        List<Question> questions
) {

    public static AdminSurveyResponse from(AdminSurveyResult result) {
        return new AdminSurveyResponse(
                AdminGenderCountResponse.from(result.members()),
                result.questions().stream().map(Question::from).toList()
        );
    }

    public record Question(
            Long id,
            String text,
            @Schema(description = "이 문항에 답한 가입자 수")
            AdminGenderCountResponse respondents,
            List<Option> options
    ) {

        static Question from(AdminSurveyResult.Question result) {
            return new Question(
                    result.id(),
                    result.text(),
                    AdminGenderCountResponse.from(result.respondents()),
                    result.options().stream().map(Option::from).toList()
            );
        }
    }

    public record Option(
            Long id,
            String label,
            @Schema(description = "이 선택지를 고른 가입자 수")
            AdminGenderCountResponse respondents
    ) {

        static Option from(AdminSurveyResult.Option result) {
            return new Option(result.id(), result.label(), AdminGenderCountResponse.from(result.respondents()));
        }
    }
}
