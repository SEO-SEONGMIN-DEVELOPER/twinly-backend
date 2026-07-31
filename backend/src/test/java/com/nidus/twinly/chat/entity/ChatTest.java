package com.nidus.twinly.chat.entity;

import com.nidus.twinly.chat.domain.ChatMessageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatTest {

    @Test
    @DisplayName("전송 시각과 생성 시각은 같은 이벤트의 시각이므로 정확히 일치한다")
    void sent_at_and_created_at_are_same_instant() {
        // when: 메시지 생성
        Chat chat = Chat.create("client-1", 10L, 1L, 2L, ChatMessageType.TEXT, "hello");

        // then: sentAt은 메시지 정렬에 쓰이는 값이라 createdAt과 어긋나면 대조가 불가능해진다
        assertThat(chat.getSentAt()).isEqualTo(chat.getCreatedAt());
    }
}
