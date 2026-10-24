package com.nidus.twinly.admin.dto.result;

import java.time.LocalDate;
import java.util.List;

public record AdminUsageResult(
        AdminGenderCountResult members,
        AdminGenderCountResult subscribers,
        AdminGenderCountResult balanceGameUsers,
        AdminGenderCountResult hesitationAnswerUsers,
        AdminGenderCountResult parallelRelationUsers,
        long openedRooms,
        long bothEnteredRooms,
        AdminGenderCountResult openedEntries,
        AdminGenderCountResult enteredEntries,
        PartnerGender malePartners,
        PartnerGender femalePartners,
        AdminGenderCountResult everSeasonParticipants,
        AdminGenderCountResult peopleFavoriteUsers,
        List<Daily> daily
) {

    public record Daily(
            LocalDate date,
            AdminGenderCountResult balanceGameUsers,
            AdminGenderCountResult hesitationAnswerUsers,
            AdminGenderCountResult parallelRelationUsers,
            AdminGenderCountResult chatRoomOpenedUsers,
            AdminGenderCountResult chatRoomEnteredUsers,
            AdminGenderCountResult peopleFavoriteUsers
    ) {
    }

    public record PartnerGender(
            long sameGender,
            long oppositeGender
    ) {
    }
}
