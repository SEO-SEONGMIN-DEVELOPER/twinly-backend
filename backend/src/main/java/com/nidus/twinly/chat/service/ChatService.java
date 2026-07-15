package com.nidus.twinly.chat.service;

import com.nidus.twinly.chat.domain.ChatMessageType;
import com.nidus.twinly.chat.dto.command.ChatSendMessageCommand;
import com.nidus.twinly.chat.dto.result.*;
import com.nidus.twinly.chat.entity.Chat;
import com.nidus.twinly.chat.entity.ChatRoomParticipation;
import com.nidus.twinly.chat.repository.ChatRepository;
import com.nidus.twinly.chat.repository.ChatRoomParticipationRepository;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.websocket.dto.ChatMessageReceivedEvent;
import com.nidus.twinly.common.websocket.registry.ChatSessionRegistry;
import com.nidus.twinly.match.entity.Match;
import com.nidus.twinly.match.repository.MatchRepository;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PhotoRepository;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    @Value("${app.current-season}")
    private Integer currentSeason;

    private final CloudFrontService cloudFrontService;

    private final ObjectMapper objectMapper;

    private final UserRepository userRepository;
    private final MatchRepository matchRepository;
    private final ChatRepository chatRepository;
    private final ChatRoomParticipationRepository chatRoomParticipationRepository;
    private final RelationshipRepository relationshipRepository;
    private final PhotoRepository photoRepository;

    private final ChatSessionRegistry chatSessionRegistry;

    @Transactional
    public ChatSendMessageResult sendMessage(Long userId, Long matchId, ChatSendMessageCommand command) {
        if (command.text() == null || command.clientMsgId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다");
        }

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 매칭입니다."));

        Long receiverUserId = resolveReceiverId(match, userId);

        Chat chat = Chat.create(command.clientMsgId(), matchId, userId, receiverUserId, ChatMessageType.TEXT, command.text());
        chatRepository.save(chat);

        sendToReceiverUser(chat, receiverUserId);

        return new ChatSendMessageResult(chat.getId(), chat.getMessage(), chat.getSentAt(), command.clientMsgId());
    }

    private Long resolveReceiverId(Match match, Long senderId) {
        if (match.getUserAId().equals(senderId)) {
            return match.getUserBId();
        }

        if (match.getUserBId().equals(senderId)) {
            return match.getUserAId();
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 매칭의 참여자가 아닙니다.");
    }

    private void sendToReceiverUser(Chat chat, Long receiverUserId) {
        Set<WebSocketSession> receiverSessions = chatSessionRegistry.get(receiverUserId);

        for (WebSocketSession session : receiverSessions) {
            if (!session.isOpen()) {
                continue;
            }

            try {
                String payload = objectMapper.writeValueAsString(ChatMessageReceivedEvent.from(chat));
                session.sendMessage(new TextMessage(payload));
            } catch (Exception e) {
                log.warn("메시지 실시간 전달 실패: receiverId={}", receiverUserId, e);
            }
        }
    }

    public ChatRoomsResult rooms(Long userId) {
        List<Match> matches = matchRepository.findAllByUserAIdOrUserBId(userId, userId);
        List<Long> matchIds = matches.stream().map(Match::getId).toList();

        List<ChatRoomParticipation> participations = chatRoomParticipationRepository.findAllByMatchIdIn(matchIds);
        Map<Long, ChatRoomParticipation> myParticipationByMatchId = participationByMatchId(participations, userId, true);
        Map<Long, ChatRoomParticipation> partnerParticipationByMatchId = participationByMatchId(participations, userId, false);

        List<Match> visibleMatches = filterVisibleMatches(matches, myParticipationByMatchId);
        List<Long> visibleMatchIds = visibleMatches.stream().map(Match::getId).toList();

        List<Long> partnerIds = visibleMatches.stream().map(match -> partnerIdOf(match, userId)).toList();

        RoomsContext context = new RoomsContext(
                userRepository.findAllById(partnerIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity())),
                myParticipationByMatchId,
                partnerParticipationByMatchId,
                photoRepository.findAllByUserIdInAndType(partnerIds, PhotoType.PROFILE).stream()
                        .collect(Collectors.toMap(Photo::getUserId, Function.identity())),
                relationshipRepository.findLatestByUserIdAndPartnerIdIn(userId, partnerIds).stream()
                        .collect(Collectors.toMap(Relationship::getPartnerId, Function.identity())),
                chatRepository.findLatestByMatchIdIn(visibleMatchIds).stream()
                        .collect(Collectors.toMap(Chat::getMatchId, Function.identity())),
                chatRepository.countUnreadByMatchIdIn(userId, visibleMatchIds).stream()
                        .collect(Collectors.toMap(ChatRepository.UnreadCountProjection::getMatchId, ChatRepository.UnreadCountProjection::getCount))
        );

        List<ChatRoomResult> rooms = visibleMatches.stream()
                .map(match -> toChatRoomResult(match, userId, context))
                .toList();

        return new ChatRoomsResult(rooms);
    }

    private record RoomsContext(
            Map<Long, User> partnerById,
            Map<Long, ChatRoomParticipation> myParticipationByMatchId,
            Map<Long, ChatRoomParticipation> partnerParticipationByMatchId,
            Map<Long, Photo> partnerPhotoById,
            Map<Long, Relationship> relationshipByPartnerId,
            Map<Long, Chat> lastChatByMatchId,
            Map<Long, Long> unreadCountByMatchId
    ) {
    }

    private Map<Long, ChatRoomParticipation> participationByMatchId(List<ChatRoomParticipation> participations, Long userId, boolean mine) {
        return participations.stream()
                .filter(p -> mine == p.getUserId().equals(userId))
                .collect(Collectors.toMap(ChatRoomParticipation::getMatchId, Function.identity()));
    }

    private List<Match> filterVisibleMatches(List<Match> matches, Map<Long, ChatRoomParticipation> myParticipationByMatchId) {
        return matches.stream()
                .filter(match -> {
                    ChatRoomParticipation mine = myParticipationByMatchId.get(match.getId());
                    return mine == null || (mine.getLeftAt() == null && !mine.getIsHidden());
                })
                .toList();
    }

    private Long partnerIdOf(Match match, Long userId) {
        return match.getUserAId().equals(userId) ? match.getUserBId() : match.getUserAId();
    }

    private ChatRoomResult toChatRoomResult(Match match, Long userId, RoomsContext context) {
        Long partnerId = partnerIdOf(match, userId);
        User partner = context.partnerById().get(partnerId);
        ChatRoomParticipation mine = context.myParticipationByMatchId().get(match.getId());
        ChatRoomParticipation partnerParticipation = context.partnerParticipationByMatchId().get(match.getId());
        Photo partnerPhoto = context.partnerPhotoById().get(partnerId);
        Relationship relationship = context.relationshipByPartnerId().get(partnerId);
        Chat lastChat = context.lastChatByMatchId().get(match.getId());
        Long unreadCount = context.unreadCountByMatchId().getOrDefault(match.getId(), 0L);

        return new ChatRoomResult(
                match.getId(),
                match.getId(),
                new ChatRoomEntryStatusResult(
                        mine != null && mine.getEntryAgreedAt() != null,
                        partnerParticipation != null && partnerParticipation.getEntryAgreedAt() != null
                ),
                new ChatRoomPartnerResult(
                        partner.getId(),
                        partner.getNickname(),
                        partnerPhoto != null ? cloudFrontService.getSignedUrl(partnerPhoto.getKey()) : null,
                        relationship != null ? relationship.getRapport() : 0,
                        partner.getDeletedAt() != null
                ),
                lastChat != null ? lastChat.getMessage() : null,
                new ChatRoomMessagesResult(
                        unreadCount.intValue(),
                        lastChat != null ? new ChatRoomLastMessageResult(lastChat.getMessage(), lastChat.getSentAt()) : null
                ),
                match.getSeason().equals(currentSeason)
        );
    }
}
