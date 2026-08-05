package com.nidus.twinly.common.fcm;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.jackson.EnumJsonNames;
import com.nidus.twinly.device.domain.DevicePlatform;
import com.nidus.twinly.device.entity.Device;
import com.nidus.twinly.notification.domain.AppNotificationFeedTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PushMessageBuilder {

    private static final String VERSION = "1";
    private static final String THREAD_ID_PREFIX = "chat-";
    private static final String DEFAULT_SOUND = "default";
    private static final Duration PHOTO_URL_EXPIRES_IN = Duration.ofHours(24);

    private final CloudFrontService cloudFrontService;

    public List<PushMessage> feed(List<Device> devices, FeedPushContent content) {
        Map<String, String> data = feedData(content);

        return devices.stream()
                .map(device -> new PushMessage(device.getPushToken(), feedMessage(device, content, data)))
                .toList();
    }

    public List<PushMessage> chatMessage(List<Device> devices, ChatMessagePushContent content) {
        Map<String, String> data = chatData(content);

        return devices.stream()
                .map(device -> new PushMessage(device.getPushToken(), device.getPlatform() == DevicePlatform.IOS
                        ? iosChatMessage(device, content, data)
                        : androidChatMessage(device, content, data)))
                .toList();
    }

    private Map<String, String> feedData(FeedPushContent content) {
        return Map.of(
                "version", VERSION,
                "type", EnumJsonNames.of(PushType.from(content.type())),
                "appNotificationId", String.valueOf(content.appNotificationId()),
                "targetKind", EnumJsonNames.of(content.targetKind()),
                "targetId", String.valueOf(content.targetId()),
                "createdAt", content.createdAt().toString());
    }

    private Message feedMessage(Device device, FeedPushContent content, Map<String, String> data) {
        return Message.builder()
                .setToken(device.getPushToken())
                .setNotification(notification(content.title(), content.body()))
                .putAllData(data)
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder().setSound(DEFAULT_SOUND).build())
                        .build())
                .build();
    }

    private Map<String, String> chatData(ChatMessagePushContent content) {
        Map<String, String> data = new HashMap<>();

        data.put("version", VERSION);
        data.put("type", EnumJsonNames.of(PushType.CHAT_MESSAGE));
        data.put("messageId", String.valueOf(content.messageId()));
        data.put("targetKind", EnumJsonNames.of(AppNotificationFeedTargetType.CHAT));
        data.put("targetId", String.valueOf(content.roomId()));
        data.put("senderId", String.valueOf(content.senderId()));
        data.put("createdAt", content.createdAt().toString());

        if (content.senderThumbnailKey() != null) {
            data.put("senderPhotoUrl", cloudFrontService.getSignedUrl(content.senderThumbnailKey(), PHOTO_URL_EXPIRES_IN));
        }

        return data;
    }

    private Message iosChatMessage(Device device, ChatMessagePushContent content, Map<String, String> data) {
        return Message.builder()
                .setToken(device.getPushToken())
                .setNotification(notification(content.senderName(), content.text()))
                .putAllData(data)
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder()
                                .setThreadId(THREAD_ID_PREFIX + content.roomId())
                                .setSound(DEFAULT_SOUND)
                                .setMutableContent(true)
                                .build())
                        .build())
                .build();
    }

    private Message androidChatMessage(Device device, ChatMessagePushContent content, Map<String, String> data) {
        Map<String, String> dataWithText = new HashMap<>(data);
        dataWithText.put("title", content.senderName());
        dataWithText.put("body", content.text());

        return Message.builder()
                .setToken(device.getPushToken())
                .putAllData(dataWithText)
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .build())
                .build();
    }

    private Notification notification(String title, String body) {
        return Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build();
    }
}
