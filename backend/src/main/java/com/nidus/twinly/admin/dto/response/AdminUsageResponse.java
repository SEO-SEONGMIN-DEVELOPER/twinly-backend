package com.nidus.twinly.admin.dto.response;

import com.nidus.twinly.admin.dto.result.AdminUsageResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record AdminUsageResponse(
        @Schema(description = "구독 없이 쓰는 기능(평행우주 관계)의 사용 비율 분모가 되는 가입자 수")
        AdminGenderCountResponse members,
        @Schema(description = "사용 비율의 분모가 되는 시즌 참가자(현재 시즌) 수")
        AdminGenderCountResponse subscribers,
        BalanceGame balanceGame,
        Hesitation hesitation,
        ParallelRelation parallelRelation,
        ChatRoom chatRoom,
        PeopleFavorite peopleFavorite
) {

    public static AdminUsageResponse from(AdminUsageResult result) {
        return new AdminUsageResponse(
                AdminGenderCountResponse.from(result.members()),
                AdminGenderCountResponse.from(result.subscribers()),
                new BalanceGame(AdminGenderCountResponse.from(result.balanceGameUsers())),
                new Hesitation(AdminGenderCountResponse.from(result.hesitationAnswerUsers())),
                new ParallelRelation(AdminGenderCountResponse.from(result.parallelRelationUsers())),
                new ChatRoom(
                        result.openedRooms(),
                        result.bothEnteredRooms(),
                        AdminGenderCountResponse.from(result.openedEntries()),
                        AdminGenderCountResponse.from(result.enteredEntries()),
                        PartnerGender.from(result.malePartners()),
                        PartnerGender.from(result.femalePartners())),
                new PeopleFavorite(
                        AdminGenderCountResponse.from(result.everSeasonParticipants()),
                        AdminGenderCountResponse.from(result.peopleFavoriteUsers()))
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

    public record ParallelRelation(
            @Schema(description = "평행우주 관계 결과를 한 번이라도 받은 가입자 수. 코드를 발급한 쪽과 등록한 쪽 모두 센다")
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

    public record PeopleFavorite(
            @Schema(description = "이용자 수. 한 번이라도 시즌에 참가한 회원 수이고 사용 비율의 분모가 된다. 탈퇴·탈퇴 유예 회원 포함, 시드 제외")
            AdminGenderCountResponse participants,
            @Schema(description = "인연 탭에서 한 번이라도 즐겨찾기한 회원 수. 지금은 해제한 회원도 센다. 탈퇴·탈퇴 유예 회원 포함, 시드 제외")
            AdminGenderCountResponse favoritedUsers
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
