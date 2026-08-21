package com.nidus.twinly.common.openapi;

import com.nidus.twinly.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "springwolf.enabled=true"
})
class ApiDocsSchemaIntegrationTest extends AbstractIntegrationTest {

    private static final String CHAT_MESSAGE_PAYLOAD = "com.nidus.twinly.chat.dto.websocket.ChatMessagePayload";

    @Test
    @DisplayName("REST 문서: 응답 스키마의 모든 프로퍼티가 required 로 표시된다")
    void openapi_marks_every_property_required() throws Exception {
        // given: springdoc 이 켜진 컨텍스트 (컨버터는 ModelConverter 빈으로 등록됨)

        // when: OpenAPI 문서 조회
        var result = mockMvc.perform(get("/docs/openapi").accept(MediaType.APPLICATION_JSON));

        // then: 내 프로필 응답의 일곱 필드가 전부 required
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.MeProfileResponse.required", containsInAnyOrder(
                        "userId", "userName", "profilePhoto", "persona", "interests",
                        "encounteredPeopleCount", "encounteredFriendCount")));

        // then: required 이면서 null 이 올 수 있는 필드는 OpenAPI 3.1 문법(type 에 "null" 포함)으로 표시된다 (@JsonFormat(STRING) 변환을 거쳐도 유지)
        result.andExpect(jsonPath("$.components.schemas.ChatMessagesResponse.required", hasItem("lastReadMessageId")))
                .andExpect(jsonPath("$.components.schemas.ChatMessagesResponse.properties.lastReadMessageId.type", hasItem("null")))
                .andExpect(jsonPath("$.components.schemas.ChatMessageItemResponse.properties.clientMsgId.type", hasItem("null")))
                .andExpect(jsonPath("$.components.schemas.ChatMessageItemResponse.properties.text.type", is("string")));

        // then: 탈퇴 시 마스킹 문자열로 대체되는 userName 은 null 타입을 허용하지 않고, 사진은 없을 수 있다
        result.andExpect(jsonPath("$.components.schemas.PeopleItemResponse.properties.userName.type", is("string")))
                .andExpect(jsonPath("$.components.schemas.PeopleItemResponse.required", hasItem("userName")));
    }

    @Test
    @DisplayName("WebSocket 문서: 같은 컨버터가 적용되어 @JsonInclude(NON_NULL) 필드만 required 에서 빠지고 null 타입은 허용하지 않는다")
    void asyncapi_excludes_null_omitting_property_from_required() throws Exception {
        // given: Springwolf 가 켜진 컨텍스트 (같은 ModelConverter 빈을 수집), clientMsgId 는 NON_NULL + nullable 아님

        // when: AsyncAPI 문서 조회
        var result = mockMvc.perform(get("/docs/asyncapi").accept(MediaType.APPLICATION_JSON));

        // then: clientMsgId 만 required 에서 빠지고, 있을 때는 항상 문자열(null 타입 없음)
        String schema = "$.components.schemas['" + CHAT_MESSAGE_PAYLOAD + "']";
        result.andExpect(status().isOk())
                .andExpect(jsonPath(schema + ".required", containsInAnyOrder(
                        "messageId", "senderType", "text", "sentAt")))
                .andExpect(jsonPath(schema + ".properties.clientMsgId.type", is("string")))
                .andExpect(jsonPath(schema + ".properties.text.type", is("string")));

        // then: 거절 페이로드의 에코 필드는 required 이되 null 타입을 허용한다 (요청에서 빠졌으면 돌려줄 값이 없음)
        String readRejected = "$.components.schemas['com.nidus.twinly.chat.dto.websocket.ChatReadRejectedPayload']";
        String messageRejected = "$.components.schemas['com.nidus.twinly.chat.dto.websocket.ChatMessageRejectedPayload']";
        result.andExpect(jsonPath(readRejected + ".required", containsInAnyOrder("roomId", "lastMsgId", "error")))
                .andExpect(jsonPath(readRejected + ".properties.roomId.type", hasItem("null")))
                .andExpect(jsonPath(readRejected + ".properties.lastMsgId.type", hasItem("null")))
                .andExpect(jsonPath(messageRejected + ".properties.roomId.type", hasItem("null")))
                .andExpect(jsonPath(messageRejected + ".properties.clientMsgId.type", hasItem("null")));
    }
}
