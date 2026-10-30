package com.nidus.twinly.chat.opener;

import com.nidus.twinly.chat.entity.ChatRoom;
import com.nidus.twinly.chat.entity.ChatRoomParticipation;
import com.nidus.twinly.chat.event.ChatChangedEvent;
import com.nidus.twinly.chat.repository.ChatRoomParticipationRepository;
import com.nidus.twinly.chat.repository.ChatRoomRepository;
import com.nidus.twinly.match.entity.Match;
import com.nidus.twinly.match.repository.MatchRepository;
import com.nidus.twinly.notification.writer.AppNotificationFeedWriter;
import com.nidus.twinly.relationship.domain.RelationshipType;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ChatRoomOpener {

    private final MatchRepository matchRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipationRepository chatRoomParticipationRepository;
    private final AppNotificationFeedWriter appNotificationFeedWriter;
    private final CurrentSeasonReader currentSeasonReader;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void openIfEligible(Long userId, Long partnerUserId, Integer intimacy) {
        if (RelationshipType.fromIntimacy(intimacy) != RelationshipType.BEST_FRIEND) {
            return;
        }

        open(userId, partnerUserId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void open(Long userId, Long partnerUserId) {
        Long currentSeasonId = currentSeasonReader.read().getId();

        Match match = matchRepository
                .findByUserAIdAndUserBId(Math.min(userId, partnerUserId), Math.max(userId, partnerUserId))
                .orElseGet(() -> matchRepository.save(Match.create(userId, partnerUserId, currentSeasonId)));

        Optional<ChatRoom> existingRoom = chatRoomRepository.findByMatchId(match.getId());
        if (existingRoom.isPresent()) {
            reopenInCurrentSeason(match, existingRoom.get(), userId, partnerUserId, currentSeasonId);
            return;
        }

        ChatRoom room = chatRoomRepository.save(ChatRoom.create(match.getId()));
        chatRoomParticipationRepository.saveAll(List.of(
                ChatRoomParticipation.create(room.getId(), userId),
                ChatRoomParticipation.create(room.getId(), partnerUserId)
        ));

        notifyOpened(room.getId(), userId, partnerUserId);
    }

    private void reopenInCurrentSeason(Match match, ChatRoom room, Long userId, Long partnerUserId, Long currentSeasonId) {
        if (match.getSeasonId().equals(currentSeasonId)) {
            return;
        }

        List<ChatRoomParticipation> participations = chatRoomParticipationRepository.findAllByRoomId(room.getId());
        boolean bothEntered = participations.size() == 2
                && participations.stream().allMatch(participation -> participation.getEntryAgreedAt() != null);
        if (bothEntered) {
            return;
        }

        match.moveToSeason(currentSeasonId);

        notifyOpened(room.getId(), userId, partnerUserId);
    }

    private void notifyOpened(Long roomId, Long userId, Long partnerUserId) {
        appNotificationFeedWriter.writeMatch(roomId, userId, partnerUserId);

        eventPublisher.publishEvent(new ChatChangedEvent(roomId, List.of(userId, partnerUserId)));
    }
}
