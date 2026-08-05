package com.nidus.twinly.chat.service;

import com.nidus.twinly.chat.domain.ChatMessageType;
import com.nidus.twinly.chat.domain.ChatSenderType;
import com.nidus.twinly.chat.dto.command.ChatReadMessagesCommand;
import com.nidus.twinly.chat.dto.command.ChatSendMessageCommand;
import com.nidus.twinly.chat.dto.result.*;
import com.nidus.twinly.chat.entity.Chat;
import com.nidus.twinly.chat.entity.ChatRoom;
import com.nidus.twinly.chat.entity.ChatRoomParticipation;
import com.nidus.twinly.chat.repository.ChatRepository;
import com.nidus.twinly.chat.repository.ChatRoomParticipationRepository;
import com.nidus.twinly.chat.repository.ChatRoomRepository;
import com.nidus.twinly.block.entity.Block;
import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.chat.event.ChatMessageCreatedEvent;
import com.nidus.twinly.chat.event.ChatReadAdvancedEvent;
import com.nidus.twinly.match.entity.Match;
import com.nidus.twinly.match.repository.MatchRepository;
import com.nidus.twinly.notification.writer.AppNotificationFeedWriter;
import com.nidus.twinly.relationship.domain.RelationshipSpecificType;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.user.domain.DisclosureField;
import com.nidus.twinly.user.entity.DisclosureAgreement;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.DisclosureAgreementRepository;
import com.nidus.twinly.user.repository.PhotoRepository;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private static final int DEFAULT_MESSAGES_LIMIT = 20;
    private static final int MAX_TEXT_BYTES = 4 * 1024;

    private final CloudFrontService cloudFrontService;

    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final MatchRepository matchRepository;
    private final ChatRepository chatRepository;
    private final ChatRoomParticipationRepository chatRoomParticipationRepository;
    private final RelationshipRepository relationshipRepository;
    private final PhotoRepository photoRepository;
    private final DisclosureAgreementRepository disclosureAgreementRepository;
    private final BlockRepository blockRepository;
    private final CurrentSeasonReader currentSeasonReader;
    private final AppNotificationFeedWriter appNotificationFeedWriter;

    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ChatSendMessageResult sendMessage(Long userId, Long roomId, ChatSendMessageCommand command) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        Match match = matchRepository.findById(room.getMatchId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_NOT_FOUND));

        Long receiverUserId = resolvePartnerId(match, userId);

        List<ChatRoomParticipation> participations = chatRoomParticipationRepository.findAllByRoomId(roomId);
        checkActiveParticipant(participations, userId);

        if (room.getClosedAt() != null) {
            throw new BusinessException(ErrorCode.ROOM_CLOSED);
        }

        if (command.text().getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) {
            throw new BusinessException(ErrorCode.MESSAGE_LENGTH_EXCEEDED);
        }

        Optional<Chat> existing = chatRepository.findBySenderUserIdAndClientMsgId(userId, command.clientMsgId());
        if (existing.isPresent()) {
            Chat sentChat = existing.get();

            if (!sentChat.getRoomId().equals(roomId) || !sentChat.getMessage().equals(command.text())) {
                throw new BusinessException(ErrorCode.CLIENT_MSG_ID_CONFLICT);
            }

            return new ChatSendMessageResult(sentChat.getId(), sentChat.getMessage(), sentChat.getSentAt(), sentChat.getClientMsgId());
        }

        Chat chat = Chat.create(command.clientMsgId(), roomId, userId, receiverUserId, ChatMessageType.TEXT, command.text());
        chatRepository.save(chat);

        eventPublisher.publishEvent(new ChatMessageCreatedEvent(chat, activeParticipantIds(participations)));

        return new ChatSendMessageResult(chat.getId(), chat.getMessage(), chat.getSentAt(), command.clientMsgId());
    }

    private ProfilePhotoInfo toProfilePhotoInfo(User partner, Photo photo) {
        if (partner.isWithdrawn() || photo == null) {
            return null;
        }
        return new ProfilePhotoInfo(photo.getKey(), cloudFrontService.getSignedUrl(photo.getKey()), photo.position());
    }

    private Long resolvePartnerId(Match match, Long senderId) {
        checkUserInMatch(match, senderId);

        return match.getUserAId().equals(senderId) ? match.getUserBId() : match.getUserAId();
    }

    private void checkUserInMatch(Match match, Long userId) {
        if (!match.getUserAId().equals(userId) && !match.getUserBId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_MATCH_PARTICIPANT);
        }
    }

    private void checkActiveParticipant(List<ChatRoomParticipation> participations, Long userId) {
        ChatRoomParticipation mine = participations.stream()
                .filter(participation -> participation.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_PARTICIPATION_NOT_FOUND));

        checkParticipationActive(mine);
    }

    private ChatRoomParticipation getActiveParticipation(Long roomId, Long userId) {
        ChatRoomParticipation participation = chatRoomParticipationRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_PARTICIPATION_NOT_FOUND));

        checkParticipationActive(participation);

        return participation;
    }

    private void checkParticipationActive(ChatRoomParticipation participation) {
        if (!participation.isActive()) {
            throw new BusinessException(ErrorCode.NOT_ACTIVE_ROOM_PARTICIPANT);
        }
    }

    private List<Long> activeParticipantIds(List<ChatRoomParticipation> participations) {
        return participations.stream()
                .filter(ChatRoomParticipation::isActive)
                .map(ChatRoomParticipation::getUserId)
                .toList();
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

        Set<Long> blockedUserIds = blockRepository.findAllByUserId(userId).stream()
                .map(Block::getBlockedUserId)
                .collect(Collectors.toSet());

        List<ChatRoom> visibleRooms = filterVisibleRooms(rooms, myParticipationByRoomId).stream()
                .filter(room -> !blockedUserIds.contains(resolvePartnerId(matchById.get(room.getMatchId()), userId)))
                .toList();
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

                relationshipRepository.findLatestByUserIdAndPartnerUserIdIn(userId, partnerIds).stream()
                        .collect(Collectors.toMap(Relationship::getPartnerUserId, Function.identity())),

                chatRepository.findLatestByRoomIdIn(visibleRoomIds).stream()
                        .collect(Collectors.toMap(Chat::getRoomId, Function.identity())),

                chatRepository.countUnreadByRoomIdIn(userId, visibleRoomIds).stream()
                        .collect(Collectors.toMap(ChatRepository.UnreadCountProjection::getRoomId, ChatRepository.UnreadCountProjection::getCount))
        );

        Long currentSeasonId = currentSeasonReader.read().getId();

        List<ChatRoomResult> roomResults = visibleRooms.stream()
                .map(room -> toChatRoomResult(room, userId, context, currentSeasonId))
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
                    return mine == null || mine.isActive();
                })
                .toList();
    }

    private ChatRoomResult toChatRoomResult(ChatRoom room, Long userId, RoomsContext context, Long currentSeasonId) {
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
                        partner.displayName(),
                        toProfilePhotoInfo(partner, partnerPhoto),
                        relationship != null ? relationship.getIntimacy() : 0,
                        partner.isWithdrawn()
                ),
                lastChat != null ? lastChat.getMessage() : null,
                new ChatRoomMessagesResult(
                        unreadCount.intValue(),
                        lastChat != null ? new ChatRoomLastMessageResult(lastChat.getMessage(), lastChat.getSentAt()) : null
                ),
                room.getClosedAt(),
                room.getCloseReason(),
                match.getSeasonId().equals(currentSeasonId)
        );
    }

    public ChatRoomDetailResult roomDetail(Long userId, Long roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        Match match = matchRepository.findById(room.getMatchId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_NOT_FOUND));

        Long partnerId = resolvePartnerId(match, userId);
        User partner = userRepository.findById(partnerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        ChatRoomParticipation myParticipation = getActiveParticipation(roomId, userId);
        ChatRoomParticipation partnerParticipation = chatRoomParticipationRepository.findByRoomIdAndUserId(roomId, partnerId)
                .orElse(null);

        Photo partnerPhoto = partner.isWithdrawn() ? null
                : photoRepository.findByUserIdAndType(partnerId, PhotoType.PROFILE)
                        .orElse(null);

        Integer intimacy = relationshipRepository.findLatestByUserIdAndPartnerUserId(userId, partnerId)
                .map(Relationship::getIntimacy)
                .orElse(0);

        Set<DisclosureField> agreedFields = partner.isWithdrawn() ? Set.of()
                : disclosureAgreementRepository.findAllByUserId(partnerId).stream()
                        .map(DisclosureAgreement::getField)
                        .collect(Collectors.toSet());

        Long currentSeasonId = currentSeasonReader.read().getId();

        return new ChatRoomDetailResult(
                room.getId(),
                match.getId(),
                new ChatRoomEntryStatusResult(
                        myParticipation.getEntryAgreedAt() != null,
                        partnerParticipation != null && partnerParticipation.getEntryAgreedAt() != null
                ),
                new ChatRoomDetailPartnerResult(
                        partner.getId(),
                        partner.displayName(),
                        toProfilePhotoInfo(partner, partnerPhoto),
                        intimacy,
                        RelationshipSpecificType.fromIntimacy(intimacy),
                        new ChatRoomDetailDisclosedFieldsResult(
                                agreedFields.contains(DisclosureField.AFFILIATION) ? partner.getAffiliation() : null,
                                agreedFields.contains(DisclosureField.AFFILIATION_NUMBER) ? partner.getAffiliationNumber() : null
                        )
                ),
                room.getClosedAt(),
                room.getCloseReason(),
                match.getSeasonId().equals(currentSeasonId)
        );
    }

    @Transactional
    public ChatRoomDetailResult enterRoom(Long userId, Long roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        Match match = matchRepository.findById(room.getMatchId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_NOT_FOUND));

        Long partnerUserId = resolvePartnerId(match, userId);

        ChatRoomParticipation mine = getActiveParticipation(roomId, userId);
        mine.agree();

        return roomDetail(userId, roomId);
    }

    @Transactional
    public void hideRoom(Long userId, Long roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        Match match = matchRepository.findById(room.getMatchId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_NOT_FOUND));

        checkUserInMatch(match, userId);

        ChatRoomParticipation mine = chatRoomParticipationRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_PARTICIPATION_NOT_FOUND));

        mine.hide();
    }

    public ChatMessagesResult messages(Long userId, Long roomId, Long cursor, Integer limit) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        Match match = matchRepository.findById(room.getMatchId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_NOT_FOUND));

        Long partnerUserId = resolvePartnerId(match, userId);
        getActiveParticipation(roomId, userId);

        Long lastReadMessageId = chatRoomParticipationRepository.findByRoomIdAndUserId(roomId, partnerUserId)
                .map(ChatRoomParticipation::getLastReadMessageId)
                .orElse(null);

        int effectiveLimit = limit != null ? limit : DEFAULT_MESSAGES_LIMIT;

        List<Chat> chats = chatRepository.findBeforeCursorByRoomId(roomId, cursor, effectiveLimit + 1);

        boolean hasMore = chats.size() > effectiveLimit;

        List<Chat> pageChats = hasMore ? chats.subList(0, effectiveLimit) : chats;

        List<ChatMessageItemResult> messages = pageChats.stream()
                .map(chat -> new ChatMessageItemResult(
                        chat.getId(),
                        chat.getSenderUserId().equals(userId) ? ChatSenderType.ME : ChatSenderType.THEM,
                        chat.getMessage(),
                        chat.getSentAt(),
                        chat.getClientMsgId()
                ))
                .toList();

        Long nextCursor = hasMore ? pageChats.getLast().getId() : null;

        return new ChatMessagesResult(roomId, messages, lastReadMessageId, new ChatMessagesPageResult(nextCursor, hasMore));
    }

    @Transactional
    public ChatReadMessagesResult readMessages(Long userId, Long roomId, ChatReadMessagesCommand command) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        Match match = matchRepository.findById(room.getMatchId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_NOT_FOUND));

        checkUserInMatch(match, userId);

        getActiveParticipation(roomId, userId);

        if (!chatRepository.existsByIdAndRoomId(command.lastMsgId(), roomId)) {
            throw new BusinessException(ErrorCode.MESSAGE_NOT_IN_ROOM);
        }

        int advanced = chatRoomParticipationRepository.advanceReadPointer(roomId, userId, command.lastMsgId());

        if (advanced == 0) {
            return new ChatReadMessagesResult(roomId, currentReadPointer(roomId, userId));
        }

        List<Long> targetUserIds = activeParticipantIds(chatRoomParticipationRepository.findAllByRoomId(roomId)).stream()
                .filter(participantUserId -> !participantUserId.equals(userId))
                .toList();

        if (!targetUserIds.isEmpty()) {
            eventPublisher.publishEvent(new ChatReadAdvancedEvent(roomId, command.lastMsgId(), targetUserIds));
        }

        return new ChatReadMessagesResult(roomId, command.lastMsgId());
    }

    private Long currentReadPointer(Long roomId, Long userId) {
        return chatRoomParticipationRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_PARTICIPATION_NOT_FOUND))
                .getLastReadMessageId();
    }

    @Transactional
    public void leaveRoom(Long userId, Long roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        Match match = matchRepository.findById(room.getMatchId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_NOT_FOUND));

        checkUserInMatch(match, userId);

        ChatRoomParticipation mine = chatRoomParticipationRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_PARTICIPATION_NOT_FOUND));

        mine.leave();
    }
}
