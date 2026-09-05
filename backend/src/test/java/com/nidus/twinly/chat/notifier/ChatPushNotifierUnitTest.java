package com.nidus.twinly.chat.notifier;

import com.nidus.twinly.chat.domain.ChatMessageType;
import com.nidus.twinly.chat.entity.Chat;
import com.nidus.twinly.chat.event.ChatMessageCreatedEvent;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.fcm.ChatMessagePushContent;
import com.nidus.twinly.common.fcm.FcmSender;
import com.nidus.twinly.common.fcm.PushMessage;
import com.nidus.twinly.common.fcm.PushType;
import com.nidus.twinly.common.fcm.PushMessageBuilder;
import com.nidus.twinly.common.fcm.PushRecipientResolver;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.device.domain.DevicePlatform;
import com.nidus.twinly.device.entity.Device;
import com.nidus.twinly.notification.domain.NotificationType;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PhotoRepository;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ChatPushNotifierUnitTest {

    private static final Long SENDER = 1L;
    private static final Long RECEIVER = 2L;
    private static final Long ROOM_ID = 77L;

    @Mock
    PushRecipientResolver pushRecipientResolver;

    @Mock
    PushMessageBuilder pushMessageBuilder;

    @Mock
    FcmSender fcmSender;

    @Mock
    UserRepository userRepository;

    @Mock
    PhotoRepository photoRepository;

    @InjectMocks
    ChatPushNotifier chatPushNotifier;

    @Test
    @DisplayName("발신자를 뺀 참여자에게만 CHAT 타입으로 수신자를 해석한다")
    void resolves_recipients_excluding_sender() {
        // given: 발신자와 수신자가 함께 참여 중인 방
        given(pushRecipientResolver.resolve(List.of(RECEIVER), NotificationType.CHAT)).willReturn(List.of());

        // when: 메시지 생성 이벤트 수신
        chatPushNotifier.onChatMessageCreated(new ChatMessageCreatedEvent(chat(), List.of(SENDER, RECEIVER)));

        // then: 자기 메시지로 자기 폰이 울리면 안 된다
        then(pushRecipientResolver).should().resolve(List.of(RECEIVER), NotificationType.CHAT);
    }

    @Test
    @DisplayName("보낼 기기가 없으면 발신자 정보를 조회하지도 않는다")
    void skips_when_no_devices() {
        // given: 수신자가 푸시를 껐거나 등록된 기기가 없음
        given(pushRecipientResolver.resolve(anyList(), any())).willReturn(List.of());

        // when: 메시지 생성 이벤트 수신
        chatPushNotifier.onChatMessageCreated(new ChatMessageCreatedEvent(chat(), List.of(SENDER, RECEIVER)));

        // then: 쓸데없는 조회와 발송을 하지 않는다
        then(userRepository).should(never()).findById(any());
        then(fcmSender).should(never()).send(anyList());
    }

    @Test
    @DisplayName("발신자 실명과 썸네일 key 를 담아 발송한다")
    void sends_with_sender_name_and_thumbnail() {
        // given: 썸네일이 있는 발신자
        given(pushRecipientResolver.resolve(anyList(), any())).willReturn(List.of(device()));
        given(userRepository.findById(SENDER)).willReturn(Optional.of(user()));
        given(photoRepository.findByUserIdAndType(SENDER, PhotoType.PROFILE)).willReturn(Optional.of(photo("profile/1/abc-thumb")));

        List<PushMessage> built = List.of(new PushMessage(1L, PushType.CHAT_MESSAGE, "token", null));
        ArgumentCaptor<ChatMessagePushContent> captor = ArgumentCaptor.captor();
        given(pushMessageBuilder.chatMessage(anyList(), captor.capture())).willReturn(built);

        // when: 메시지 생성 이벤트 수신
        chatPushNotifier.onChatMessageCreated(new ChatMessageCreatedEvent(chat(), List.of(SENDER, RECEIVER)));

        // then: 빌더에 넘긴 내용과 발송 위임을 함께 확인
        ChatMessagePushContent content = captor.getValue();
        assertThat(content.messageId()).isEqualTo(8821L);
        assertThat(content.roomId()).isEqualTo(ROOM_ID);
        assertThat(content.senderId()).isEqualTo(SENDER);
        assertThat(content.senderName()).isEqualTo("나야");
        assertThat(content.text()).isEqualTo("내일 시간 괜찮아?");
        assertThat(content.senderThumbnailKey()).isEqualTo("profile/1/abc-thumb");
        then(fcmSender).should().send(built);
    }

    @Test
    @DisplayName("발신자 썸네일이 없으면 key 없이 발송한다")
    void sends_without_thumbnail() {
        // given: 프로필 사진이 없는 발신자
        given(pushRecipientResolver.resolve(anyList(), any())).willReturn(List.of(device()));
        given(userRepository.findById(SENDER)).willReturn(Optional.of(user()));
        given(photoRepository.findByUserIdAndType(SENDER, PhotoType.PROFILE)).willReturn(Optional.empty());

        ArgumentCaptor<ChatMessagePushContent> captor = ArgumentCaptor.captor();
        given(pushMessageBuilder.chatMessage(anyList(), captor.capture())).willReturn(List.of());

        // when: 메시지 생성 이벤트 수신
        chatPushNotifier.onChatMessageCreated(new ChatMessageCreatedEvent(chat(), List.of(SENDER, RECEIVER)));

        // then: 아바타만 빠지고 발송은 진행된다
        assertThat(captor.getValue().senderThumbnailKey()).isNull();
    }

    @Test
    @DisplayName("발신자를 찾지 못하면 발송하지 않는다")
    void skips_when_sender_missing() {
        // given: 발신자 행이 사라진 비정상 상황
        given(pushRecipientResolver.resolve(anyList(), any())).willReturn(List.of(device()));
        given(userRepository.findById(SENDER)).willReturn(Optional.empty());

        // when: 메시지 생성 이벤트 수신
        chatPushNotifier.onChatMessageCreated(new ChatMessageCreatedEvent(chat(), List.of(SENDER, RECEIVER)));

        // then: 제목 없는 알림을 보내느니 안 보낸다
        then(fcmSender).should(never()).send(anyList());
    }

    private Chat chat() {
        Chat chat = Chat.create("client-1", ROOM_ID, SENDER, RECEIVER, ChatMessageType.TEXT, "내일 시간 괜찮아?");
        ReflectionTestUtils.setField(chat, "id", 8821L);
        ReflectionTestUtils.setField(chat, "sentAt", Instant.parse("2026-07-26T05:25:00Z"));
        return chat;
    }

    private User user() {
        return User.create(
                "nickname",
                "김", "familyHash",
                "나야", "givenHash",
                Gender.FEMALE,
                "organization", "organizationHash",
                "aff", "affHash",
                "affNo", "affNoHash",
                "2000-01-01", "birthHash",
                "phone", "phoneHash",
                "email", "emailHash", null, null
        );
    }

    private Device device() {
        return Device.create(RECEIVER, UUID.randomUUID(), DevicePlatform.IOS, "token");
    }

    private Photo photo(String thumbnailKey) {
        Photo photo = Photo.create(SENDER, PhotoType.PROFILE, "profile/1/abc", 0, 0, 100, 100, Instant.now());
        photo.changeThumbnailKey(thumbnailKey);
        return photo;
    }
}
