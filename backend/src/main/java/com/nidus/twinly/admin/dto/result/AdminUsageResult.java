package com.nidus.twinly.admin.dto.result;

public record AdminUsageResult(
        AdminGenderCountResult subscribers,
        AdminGenderCountResult balanceGameUsers,
        long openedRooms,
        long bothEnteredRooms,
        AdminGenderCountResult openedEntries,
        AdminGenderCountResult enteredEntries,
        PartnerGender malePartners,
        PartnerGender femalePartners
) {

    public record PartnerGender(
            long sameGender,
            long oppositeGender
    ) {
    }
}
