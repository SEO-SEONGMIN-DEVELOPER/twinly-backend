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

        Match match = matchRepository
                .findByUserAIdAndUserBId(Math.min(userId, partnerUserId), Math.max(userId, partnerUserId))
                .orElseGet(() -> matchRepository.save(Match.create(userId, partnerUserId, currentSeasonReader.read().getId())));

        if (chatRoomRepository.findByMatchId(match.getId()).isPresent()) {
            return;
        }

        ChatRoom room = chatRoomRepository.save(ChatRoom.create(match.getId()));
        chatRoomParticipationRepository.saveAll(List.of(
                ChatRoomParticipation.create(room.getId(), userId),
                ChatRoomParticipation.create(room.getId(), partnerUserId)
        ));

        appNotificationFeedWriter.writeMatch(room.getId(), userId, partnerUserId);

        eventPublisher.publishEvent(new ChatChangedEvent(room.getId(), List.of(userId, partnerUserId)));
    }
}
