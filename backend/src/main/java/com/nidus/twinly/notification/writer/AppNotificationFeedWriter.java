package com.nidus.twinly.notification.writer;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.notification.domain.AppNotificationFeedType;
import com.nidus.twinly.notification.entity.AppNotificationFeed;
import com.nidus.twinly.notification.event.AppNotificationFeedCreatedEvent;
import com.nidus.twinly.notification.event.SchoolVerificationFeedCreatedEvent;
import com.nidus.twinly.notification.repository.AppNotificationFeedRepository;
import com.nidus.twinly.schoolverification.domain.SchoolVerificationStatus;
import com.nidus.twinly.schoolverification.domain.StudentIdRejectReason;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AppNotificationFeedWriter {

    private static final String MATCH_BODY = "채팅 탭에서 입장하기를 누르면, 상대가 동의한 뒤 대화가 시작돼요.";
    private static final String BALANCE_GAME_RESULT_BODY = "서로 같은 답을 골랐는지 확인해 보세요.";
    private static final String BALANCE_GAME_WAITING_BODY = "나도 답하고 서로 같은 답을 골랐는지 확인해 보세요.";
    private static final String SCHOOL_VERIFICATION_APPROVED_TITLE = "학교 인증이 끝났어요";
    private static final String SCHOOL_VERIFICATION_APPROVED_BODY = "이제 평행우주에 참여할 수 있어요.";
    private static final String SCHOOL_VERIFICATION_REJECTED_TITLE = "학생증을 다시 확인해 주세요";

    private final AppNotificationFeedRepository appNotificationFeedRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public void writeMatch(Long roomId, Long userId, Long partnerUserId) {
        publish(appNotificationFeedRepository.saveAll(List.of(
                matchFeed(userId, partnerUserId, roomId),
                matchFeed(partnerUserId, userId, roomId)
        )));
    }

    public void writeFriend(Long userId, Long partnerUserId, LocalDate simulationDate) {
        appNotificationFeedRepository.deleteAllByUserIdAndTypeAndTargetUserIdAndSimulationDate(
                userId, AppNotificationFeedType.FRIEND, partnerUserId, simulationDate);

        String partnerName = name(partnerUserId);

        publish(List.of(appNotificationFeedRepository.save(AppNotificationFeed.createProfileTarget(
                userId,
                AppNotificationFeedType.FRIEND,
                partnerName + "님과 친구가 되었어요.",
                "평행세계에서 " + partnerName + "님과 친구가 되었어요.",
                partnerUserId,
                simulationDate
        ))));
    }

    public void writeBalanceGameAnswered(Long userId, Long answeredUserId, boolean userAnswered) {
        publish(List.of(appNotificationFeedRepository.save(AppNotificationFeed.createBalanceGameTarget(
                userId,
                AppNotificationFeedType.BALANCE_GAME_ANSWERED,
                name(answeredUserId) + "님이 밸런스 게임에 답했어요.",
                userAnswered ? BALANCE_GAME_RESULT_BODY : BALANCE_GAME_WAITING_BODY,
                answeredUserId
        ))));
    }

    public void writeSchoolVerificationApproved(Long userId) {
        AppNotificationFeed feed = appNotificationFeedRepository.save(AppNotificationFeed.createUntargeted(
                userId,
                AppNotificationFeedType.SCHOOL_VERIFICATION,
                SCHOOL_VERIFICATION_APPROVED_TITLE,
                SCHOOL_VERIFICATION_APPROVED_BODY
        ));

        eventPublisher.publishEvent(new SchoolVerificationFeedCreatedEvent(feed, SchoolVerificationStatus.APPROVED));
    }

    public void writeSchoolVerificationRejected(Long userId, StudentIdRejectReason rejectReason) {
        AppNotificationFeed feed = appNotificationFeedRepository.save(AppNotificationFeed.createUntargeted(
                userId,
                AppNotificationFeedType.SCHOOL_VERIFICATION,
                SCHOOL_VERIFICATION_REJECTED_TITLE,
                rejectReason.message()
        ));

        eventPublisher.publishEvent(new SchoolVerificationFeedCreatedEvent(feed, SchoolVerificationStatus.REJECTED));
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

    private void publish(List<AppNotificationFeed> feeds) {
        eventPublisher.publishEvent(new AppNotificationFeedCreatedEvent(feeds));
    }

    private String name(Long userId) {
        return userRepository.findById(userId)
                .map(User::displayNickname)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
