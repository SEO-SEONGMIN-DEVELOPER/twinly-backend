package com.nidus.twinly.admin.dto.result;

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
        AdminGenderCountResult peopleFavoriteUsers
) {

    public record PartnerGender(
            long sameGender,
            long oppositeGender
    ) {
    }
}
