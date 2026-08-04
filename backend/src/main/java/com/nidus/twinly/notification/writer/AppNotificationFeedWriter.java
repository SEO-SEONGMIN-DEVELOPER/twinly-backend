package com.nidus.twinly.notification.writer;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.notification.domain.AppNotificationFeedType;
import com.nidus.twinly.notification.entity.AppNotificationFeed;
import com.nidus.twinly.notification.repository.AppNotificationFeedRepository;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AppNotificationFeedWriter {

    private static final String MATCH_BODY = "채팅 탭에서 입장하기를 누르면, 상대가 동의한 뒤 대화가 시작돼요.";
    private static final String CHAT_READY_BODY = "두 사람 모두 입장하기를 눌렀어요. 이제 대화를 시작할 수 있어요.";

    private final AppNotificationFeedRepository appNotificationFeedRepository;
    private final UserRepository userRepository;

    public void writeMatch(Long roomId, Long userId, Long partnerUserId) {
        appNotificationFeedRepository.saveAll(List.of(
                matchFeed(userId, partnerUserId, roomId),
                matchFeed(partnerUserId, userId, roomId)
        ));
    }

    public void writeChatReady(Long roomId, Long userId, Long partnerUserId) {
        appNotificationFeedRepository.saveAll(List.of(
                chatReadyFeed(userId, partnerUserId, roomId),
                chatReadyFeed(partnerUserId, userId, roomId)
        ));
    }

    public void writeFriend(Long userId, Long partnerUserId, LocalDate simulationDate) {
        appNotificationFeedRepository.deleteAllByUserIdAndTypeAndTargetUserIdAndSimulationDate(
                userId, AppNotificationFeedType.FRIEND, partnerUserId, simulationDate);

        String partnerName = name(partnerUserId);

        appNotificationFeedRepository.save(AppNotificationFeed.createProfileTarget(
                userId,
                AppNotificationFeedType.FRIEND,
                partnerName + "님과 친구가 되었어요.",
                "평행세계에서 " + partnerName + "님과 친구가 되었어요.",
                partnerUserId,
                simulationDate
        ));
    }

    private AppNotificationFeed matchFeed(Long userId, Long partnerUserId, Long roomId) {
        return AppNotificationFeed.createChatTarget(
                userId,
                AppNotificationFeedType.MATCH,
                name(partnerUserId) + "님과 채팅방이 열렸어요.",
                MATCH_BODY,
                roomId
        );
    }

    private AppNotificationFeed chatReadyFeed(Long userId, Long partnerUserId, Long roomId) {
        return AppNotificationFeed.createChatTarget(
                userId,
                AppNotificationFeedType.CHAT_READY,
                name(partnerUserId) + "님과 채팅할 수 있어요.",
                CHAT_READY_BODY,
                roomId
        );
    }

    private String name(Long userId) {
        return userRepository.findById(userId)
                .map(User::displayName)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
