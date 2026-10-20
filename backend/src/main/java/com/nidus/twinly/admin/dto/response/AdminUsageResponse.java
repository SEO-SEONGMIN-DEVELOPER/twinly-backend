package com.nidus.twinly.admin.dto.response;

import com.nidus.twinly.admin.dto.result.AdminUsageResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record AdminUsageResponse(
        @Schema(description = "사용 비율의 분모가 되는 구독자(현재 시즌 참가자) 수")
        AdminGenderCountResponse subscribers,
        BalanceGame balanceGame,
        Hesitation hesitation,
        ChatRoom chatRoom
) {

    public static AdminUsageResponse from(AdminUsageResult result) {
        return new AdminUsageResponse(
                AdminGenderCountResponse.from(result.subscribers()),
                new BalanceGame(AdminGenderCountResponse.from(result.balanceGameUsers())),
                new Hesitation(AdminGenderCountResponse.from(result.hesitationAnswerUsers())),
                new ChatRoom(
                        result.openedRooms(),
                        result.bothEnteredRooms(),
                        AdminGenderCountResponse.from(result.openedEntries()),
                        AdminGenderCountResponse.from(result.enteredEntries()),
                        PartnerGender.from(result.malePartners()),
                        PartnerGender.from(result.femalePartners()))
        );
    }

    public record BalanceGame(
            @Schema(description = "한 번이라도 답한 가입자 수")
            AdminGenderCountResponse users
    ) {
    }

    public record Hesitation(
            @Schema(description = "망설임 질문에 한 번이라도 답한 가입자 수. 건너뛰기는 답변이 아니다")
            AdminGenderCountResponse users
    ) {
    }

    public record ChatRoom(
            @Schema(description = "열린 채팅방 수. 시드 회원이 있는 방 제외")
            Long openedRooms,
            @Schema(description = "두 사람 모두 입장한 채팅방 수")
            Long bothEnteredRooms,
            @Schema(description = "회원별로 열린 건수. 방 하나가 두 사람에게 각각 열린다")
            AdminGenderCountResponse openedEntries,
            @Schema(description = "그중 본인이 입장한 건수")
            AdminGenderCountResponse enteredEntries,
            @Schema(description = "남성 회원이 입장한 방의 상대 성별")
            PartnerGender malePartners,
            @Schema(description = "여성 회원이 입장한 방의 상대 성별")
            PartnerGender femalePartners
    ) {
    }

    public record PartnerGender(
            Long sameGender,
            Long oppositeGender
    ) {

        static PartnerGender from(AdminUsageResult.PartnerGender result) {
            return new PartnerGender(result.sameGender(), result.oppositeGender());
        }
    }
}
