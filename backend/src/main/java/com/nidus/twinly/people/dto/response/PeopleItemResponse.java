package com.nidus.twinly.people.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.people.dto.result.PeopleItemResult;
import com.nidus.twinly.relationship.domain.RelationshipSpecificType;
import com.nidus.twinly.relationship.domain.RelationshipType;
import io.swagger.v3.oas.annotations.media.Schema;

public record PeopleItemResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        @Schema(nullable = true)
        String userName,
        @Schema(nullable = true)
        String profilePhotoUrl,
        Integer intimacy,
        RelationshipType relationshipType,
        RelationshipSpecificType relationshipSpecificType,
        Integer sceneElementCount,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(nullable = true)
        Long chatRoomId,
        Boolean isFavorited,
        Boolean isHighlighted
) {

    public static PeopleItemResponse from(PeopleItemResult result) {
        return new PeopleItemResponse(
                result.userId(),
                result.userName(),
                result.profilePhotoUrl(),
                result.intimacy(),
                result.relationshipType(),
                result.relationshipSpecificType(),
                result.sceneElementCount(),
                result.chatRoomId(),
                result.isFavorited(),
                result.isHighlighted()
        );
    }
}
