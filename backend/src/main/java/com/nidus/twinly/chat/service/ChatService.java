package com.nidus.twinly.chat.service;

import com.nidus.twinly.chat.domain.ChatMessageType;
import com.nidus.twinly.chat.dto.command.ChatSendMessageCommand;
import com.nidus.twinly.chat.dto.result.*;
import com.nidus.twinly.chat.entity.Chat;
import com.nidus.twinly.chat.entity.ChatRoom;
import com.nidus.twinly.chat.entity.ChatRoomParticipation;
import com.nidus.twinly.chat.repository.ChatRepository;
import com.nidus.twinly.chat.repository.ChatRoomParticipationRepository;
import com.nidus.twinly.chat.repository.ChatRoomRepository;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.websocket.dto.ChatMessageReceivedEvent;
import com.nidus.twinly.common.websocket.registry.ChatSessionRegistry;
import com.nidus.twinly.match.entity.Match;
import com.nidus.twinly.match.repository.MatchRepository;
import com.nidus.twinly.relationship.domain.RelationshipSpecificType;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.user.domain.DisclosureField;
import com.nidus.twinly.user.entity.DisclosureAgreement;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.DisclosureAgreementRepository;
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
    private final ChatRoomRepository chatRoomRepository;
    private final MatchRepository matchRepository;
    private final ChatRepository chatRepository;
    private final ChatRoomParticipationRepository chatRoomParticipationRepository;
    private final RelationshipRepository relationshipRepository;
    private final PhotoRepository photoRepository;
    private final DisclosureAgreementRepository disclosureAgreementRepository;

    private final ChatSessionRegistry chatSessionRegistry;

    @Transactional
    public ChatSendMessageResult sendMessage(Long userId, Long roomId, ChatSendMessageCommand command) {
        if (command.text() == null || command.clientMsgId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다");
        }

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 채팅방입니다."));

        Match match = matchRepository.findById(room.getMatchId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 매칭입니다."));

        Long receiverUserId = resolvePartnerId(match, userId);

        Chat chat = Chat.create(command.clientMsgId(), roomId, userId, receiverUserId, ChatMessageType.TEXT, command.text());
        chatRepository.save(chat);

        sendToReceiverUser(chat, receiverUserId);

        return new ChatSendMessageResult(chat.getId(), chat.getMessage(), chat.getSentAt(), command.clientMsgId());
    }

    private Long resolvePartnerId(Match match, Long senderId) {
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
        Map<Long, Match> matchById = matches.stream()
                .collect(Collectors.toMap(Match::getId, Function.identity()));

        List<ChatRoom> rooms = chatRoomRepository.findAllByMatchIdIn(matchIds);
        List<Long> roomIds = rooms.stream().map(ChatRoom::getId).toList();

        List<ChatRoomParticipation> participations = chatRoomParticipationRepository.findAllByRoomIdIn(roomIds);
        Map<Long, ChatRoomParticipation> myParticipationByRoomId = participationByRoomId(participations, userId, true);
        Map<Long, ChatRoomParticipation> partnerParticipationByRoomId = participationByRoomId(participations, userId, false);

        List<ChatRoom> visibleRooms = filterVisibleRooms(rooms, myParticipationByRoomId);
        List<Long> visibleRoomIds = visibleRooms.stream().map(ChatRoom::getId).toList();

        List<Long> partnerIds = visibleRooms.stream().map(room -> resolvePartnerId(matchById.get(room.getMatchId()), userId)).toList();

        RoomsContext context = new RoomsContext(
                matchById,

                userRepository.findAllById(partnerIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity())),

                myParticipationByRoomId,

                partnerParticipationByRoomId,

                photoRepository.findAllByUserIdInAndType(partnerIds, PhotoType.PROFILE).stream()
                        .collect(Collectors.toMap(Photo::getUserId, Function.identity())),

                relationshipRepository.findLatestByUserIdAndPartnerIdIn(userId, partnerIds).stream()
                        .collect(Collectors.toMap(Relationship::getPartnerId, Function.identity())),

                chatRepository.findLatestByRoomIdIn(visibleRoomIds).stream()
                        .collect(Collectors.toMap(Chat::getRoomId, Function.identity())),

                chatRepository.countUnreadByRoomIdIn(userId, visibleRoomIds).stream()
                        .collect(Collectors.toMap(ChatRepository.UnreadCountProjection::getRoomId, ChatRepository.UnreadCountProjection::getCount))
        );

        List<ChatRoomResult> roomResults = visibleRooms.stream()
                .map(room -> toChatRoomResult(room, userId, context))
                .toList();

        return new ChatRoomsResult(roomResults);
    }

    private record RoomsContext(
            Map<Long, Match> matchById,
            Map<Long, User> partnerById,
            Map<Long, ChatRoomParticipation> myParticipationByRoomId,
            Map<Long, ChatRoomParticipation> partnerParticipationByRoomId,
            Map<Long, Photo> partnerPhotoById,
            Map<Long, Relationship> relationshipByPartnerId,
            Map<Long, Chat> lastChatByRoomId,
            Map<Long, Long> unreadCountByRoomId
    ) {
    }

    private Map<Long, ChatRoomParticipation> participationByRoomId(List<ChatRoomParticipation> participations, Long userId, boolean mine) {
        return participations.stream()
                .filter(p -> mine == p.getUserId().equals(userId))
                .collect(Collectors.toMap(ChatRoomParticipation::getRoomId, Function.identity()));
    }

    private List<ChatRoom> filterVisibleRooms(List<ChatRoom> rooms, Map<Long, ChatRoomParticipation> myParticipationByRoomId) {
        return rooms.stream()
                .filter(room -> {
                    ChatRoomParticipation mine = myParticipationByRoomId.get(room.getId());
                    return mine == null || (mine.getLeftAt() == null && !mine.getIsHidden());
                })
                .toList();
    }

    private ChatRoomResult toChatRoomResult(ChatRoom room, Long userId, RoomsContext context) {
        Match match = context.matchById().get(room.getMatchId());
        Long partnerId = resolvePartnerId(match, userId);
        User partner = context.partnerById().get(partnerId);
        ChatRoomParticipation myParticipation = context.myParticipationByRoomId().get(room.getId());
        ChatRoomParticipation partnerParticipation = context.partnerParticipationByRoomId().get(room.getId());
        Photo partnerPhoto = context.partnerPhotoById().get(partnerId);
        Relationship relationship = context.relationshipByPartnerId().get(partnerId);
        Chat lastChat = context.lastChatByRoomId().get(room.getId());
        Long unreadCount = context.unreadCountByRoomId().getOrDefault(room.getId(), 0L);

        return new ChatRoomResult(
                room.getId(),
                match.getId(),
                new ChatRoomEntryStatusResult(
                        myParticipation != null && myParticipation.getEntryAgreedAt() != null,
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
                room.getClosedAt(),
                room.getCloseReason(),
                match.getSeason().equals(currentSeason)
        );
    }

    public ChatRoomDetailResult roomDetail(Long userId, Long roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 채팅방입니다."));

        Match match = matchRepository.findById(room.getMatchId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 매칭입니다."));

        Long partnerId = resolvePartnerId(match, userId);
        User partner = userRepository.findById(partnerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 유저입니다."));

        ChatRoomParticipation myParticipation = chatRoomParticipationRepository.findByRoomIdAndUserId(roomId, userId)
                .orElse(null);
        ChatRoomParticipation partnerParticipation = chatRoomParticipationRepository.findByRoomIdAndUserId(roomId, partnerId)
                .orElse(null);

        Photo partnerPhoto = photoRepository.findByUserIdAndType(partnerId, PhotoType.PROFILE)
                .orElse(null);

        Integer rapport = relationshipRepository.findLatestByUserIdAndPartnerId(userId, partnerId)
                .map(Relationship::getRapport)
                .orElse(0);

        Set<DisclosureField> agreedFields = disclosureAgreementRepository.findAllByUserId(partnerId).stream()
                .map(DisclosureAgreement::getField)
                .collect(Collectors.toSet());

        return new ChatRoomDetailResult(
                room.getId(),
                match.getId(),
                new ChatRoomEntryStatusResult(
                        myParticipation != null && myParticipation.getEntryAgreedAt() != null,
                        partnerParticipation != null && partnerParticipation.getEntryAgreedAt() != null
                ),
                new ChatRoomDetailPartnerResult(
                        partner.getId(),
                        partner.getNickname(),
                        partnerPhoto != null ? cloudFrontService.getSignedUrl(partnerPhoto.getKey()) : null,
                        rapport,
                        RelationshipSpecificType.fromRapport(rapport),
                        new ChatRoomDetailDisclosedFieldsResult(
                                agreedFields.contains(DisclosureField.AFFILIATION) ? partner.getAffiliation() : null,
                                agreedFields.contains(DisclosureField.BIRTH_DATE) ? partner.getBirthDate() : null
                        )
                ),
                room.getClosedAt(),
                room.getCloseReason(),
                match.getSeason().equals(currentSeason)
        );
    }
}
