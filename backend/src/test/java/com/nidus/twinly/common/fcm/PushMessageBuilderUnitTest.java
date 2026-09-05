package com.nidus.twinly.common.fcm;

import com.google.api.client.json.gson.GsonFactory;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.device.domain.DevicePlatform;
import com.nidus.twinly.device.entity.Device;
import com.nidus.twinly.notification.domain.AppNotificationFeedTargetType;
import com.nidus.twinly.notification.domain.AppNotificationFeedType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PushMessageBuilderUnitTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-26T05:25:00Z");

    @Mock
    CloudFrontService cloudFrontService;

    @InjectMocks
    PushMessageBuilder pushMessageBuilder;

    @Test
    @DisplayName("채팅 메시지: iOS 는 notification 블록과 mutable-content·thread-id 를 갖는다")
    void chatMessage_ios() throws IOException {
        // given: 아이폰 기기 하나
        Device device = device(DevicePlatform.IOS, "token-ios");

        // when: 채팅 메시지 푸시 생성
        List<PushMessage> messages = pushMessageBuilder.chatMessage(List.of(device), chatContent(null));

        // then: 알림 확장이 깨어나려면 mutable-content 가 반드시 있어야 한다
        String json = toJson(messages.get(0));
        assertThat(json).contains("\"notification\"");
        assertThat(json).contains("\"mutable-content\":1");
        assertThat(json).contains("\"thread-id\":\"chat-77\"");
        assertThat(json).contains("\"title\":\"김나야\"");
        assertThat(json).contains("\"senderId\":\"12\"");
    }

    @Test
    @DisplayName("채팅 메시지: Android 는 notification 블록 없이 title·body 를 data 에 담는다")
    void chatMessage_android() throws IOException {
        // given: 안드로이드 기기 하나
        Device device = device(DevicePlatform.ANDROID, "token-android");

        // when: 채팅 메시지 푸시 생성
        List<PushMessage> messages = pushMessageBuilder.chatMessage(List.of(device), chatContent(null));

        // then: data-only 라야 앱이 직접 아바타를 그릴 수 있다
        String json = toJson(messages.get(0));
        assertThat(json).doesNotContain("\"notification\"");
        assertThat(json).contains("\"title\":\"김나야\"");
        assertThat(json).contains("\"body\":\"내일 시간 괜찮아?\"");
        assertThat(json).contains("\"priority\":\"high\"");
    }

    @Test
    @DisplayName("채팅 메시지: 한 사용자가 두 플랫폼을 쓰면 각각 다른 모양으로 만들어진다")
    void chatMessage_mixed_platforms() throws IOException {
        // given: 같은 사용자의 아이폰과 안드로이드
        List<Device> devices = List.of(device(DevicePlatform.IOS, "token-ios"), device(DevicePlatform.ANDROID, "token-android"));

        // when: 채팅 메시지 푸시 생성
        List<PushMessage> messages = pushMessageBuilder.chatMessage(devices, chatContent(null));

        // then: 멀티캐스트로는 못 보내는 이유가 여기 있다
        assertThat(messages).hasSize(2);
        assertThat(toJson(messages.get(0))).contains("\"notification\"");
        assertThat(toJson(messages.get(1))).doesNotContain("\"notification\"");
        assertThat(messages).extracting(PushMessage::token).containsExactly("token-ios", "token-android");
    }

    @Test
    @DisplayName("채팅 메시지: 썸네일이 있으면 24시간 서명 URL 을 senderPhotoUrl 로 담는다")
    void chatMessage_with_thumbnail() throws IOException {
        // given: 썸네일 key 가 있는 발신자
        given(cloudFrontService.getSignedUrl("profile/12/uuid-thumb", Duration.ofHours(24)))
                .willReturn("https://cdn.example.com/signed");

        // when: 채팅 메시지 푸시 생성
        List<PushMessage> messages = pushMessageBuilder.chatMessage(
                List.of(device(DevicePlatform.IOS, "token-ios")), chatContent("profile/12/uuid-thumb"));

        // then: 배달 지연을 견디도록 10분이 아닌 24시간짜리 서명이어야 한다
        assertThat(toJson(messages.get(0))).contains("\"senderPhotoUrl\":\"https://cdn.example.com/signed\"");
    }

    @Test
    @DisplayName("채팅 메시지: 썸네일이 없으면 senderPhotoUrl 키 자체를 넣지 않는다")
    void chatMessage_without_thumbnail() throws IOException {
        // when: 썸네일 key 가 없는 발신자
        List<PushMessage> messages = pushMessageBuilder.chatMessage(
                List.of(device(DevicePlatform.IOS, "token-ios")), chatContent(null));

        // then: 빈 문자열이 아니라 키 자체가 없어야 클라가 if(url) 한 번으로 판단한다
        assertThat(toJson(messages.get(0))).doesNotContain("senderPhotoUrl");
    }

    @Test
    @DisplayName("알림 피드: type 과 targetKind 가 camelCase 소문자로 나간다")
    void feed_enum_values_are_camel_case() throws IOException {
        // given: 채팅방을 가리키는 match 피드
        FeedPushContent content = new FeedPushContent(
                1002L, AppNotificationFeedType.MATCH, "김나야님과 채팅방이 열렸어요.", "본문",
                AppNotificationFeedTargetType.CHAT, 77L, CREATED_AT);

        // when: 피드 푸시 생성
        List<PushMessage> messages = pushMessageBuilder.feed(List.of(device(DevicePlatform.IOS, "token-ios")), content);

        // then: data 는 Jackson 을 거치지 않으므로 표기가 어긋나기 쉽다
        String json = toJson(messages.get(0));
        assertThat(json).contains("\"type\":\"match\"");
        assertThat(json).contains("\"targetKind\":\"chat\"");
        assertThat(json).contains("\"appNotificationId\":\"1002\"");
        assertThat(json).contains("\"createdAt\":\"2026-07-26T05:25:00Z\"");
    }

    @Test
    @DisplayName("알림 피드: 플랫폼이 달라도 같은 모양으로 만들어진다")
    void feed_is_platform_agnostic() throws IOException {
        // given: 아이폰과 안드로이드
        List<Device> devices = List.of(device(DevicePlatform.IOS, "token-ios"), device(DevicePlatform.ANDROID, "token-android"));
        FeedPushContent content = new FeedPushContent(
                1002L, AppNotificationFeedType.MATCH, "제목", "본문",
                AppNotificationFeedTargetType.CHAT, 77L, CREATED_AT);

        // when: 피드 푸시 생성
        List<PushMessage> messages = pushMessageBuilder.feed(devices, content);

        // then: 커스텀 UI 가 필요 없으니 양쪽 모두 notification 블록을 쓴다
        assertThat(toJson(messages.get(0))).contains("\"notification\"");
        assertThat(toJson(messages.get(1))).contains("\"notification\"");
    }

    @Test
    @DisplayName("발송 대상 사용자와 푸시 종류를 메시지에 함께 싣는다")
    void carries_recipient_and_type() {
        // given: 서로 다른 사용자의 기기 두 대
        Device deviceA = Device.create(41L, UUID.randomUUID(), DevicePlatform.IOS, "token-A");
        Device deviceB = Device.create(52L, UUID.randomUUID(), DevicePlatform.ANDROID, "token-B");

        // when: 채팅 푸시 생성
        List<PushMessage> messages = pushMessageBuilder.chatMessage(List.of(deviceA, deviceB), chatContent(null));

        // then: 실패 로그가 누구에게 실패했는지 말하려면 여기서 실려야 한다
        assertThat(messages).extracting(PushMessage::userId).containsExactly(41L, 52L);
        assertThat(messages).extracting(PushMessage::type).containsOnly(PushType.CHAT_MESSAGE);
    }

    @Test
    @DisplayName("피드 푸시는 피드 종류에 맞는 푸시 종류를 싣는다")
    void carries_feed_type() {
        // given: 매칭 피드
        FeedPushContent content = new FeedPushContent(
                1002L, AppNotificationFeedType.MATCH, "제목", "본문",
                AppNotificationFeedTargetType.CHAT, 77L, CREATED_AT);

        // when: 피드 푸시 생성
        List<PushMessage> messages = pushMessageBuilder.feed(
                List.of(device(DevicePlatform.IOS, "token-ios")), content);

        // then: 집계에서 채팅 푸시와 섞이면 안 된다
        assertThat(messages).extracting(PushMessage::type).containsExactly(PushType.MATCH);
    }

    private Device device(DevicePlatform platform, String token) {
        return Device.create(1L, UUID.randomUUID(), platform, token);
    }

    private ChatMessagePushContent chatContent(String thumbnailKey) {
        return new ChatMessagePushContent(8821L, 77L, 12L, "김나야", "내일 시간 괜찮아?", thumbnailKey, CREATED_AT);
    }

    private String toJson(PushMessage pushMessage) throws IOException {
        return GsonFactory.getDefaultInstance().toString(pushMessage.message());
    }
}
