package com.nidus.twinly.chat.service;

import com.nidus.twinly.chat.entity.ChatRoomOpening;
import com.nidus.twinly.chat.opener.ChatRoomOpener;
import com.nidus.twinly.chat.repository.ChatRoomOpeningRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomOpeningService {

    private final ChatRoomOpeningRepository chatRoomOpeningRepository;
    private final ChatRoomOpener chatRoomOpener;

    public int openDue(Instant now) {
        List<ChatRoomOpening> due =
                chatRoomOpeningRepository.findAllByOpenedAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(now);
        int opened = 0;

        for (ChatRoomOpening opening : due) {
            try {
                chatRoomOpener.open(opening.getUserAId(), opening.getUserBId());
                opened++;
            } catch (DataIntegrityViolationException e) {
                log.info("상대 쪽에서 채팅방을 먼저 열어 개설을 건너뜁니다. userAId={}, userBId={}",
                        opening.getUserAId(), opening.getUserBId());
            }

            opening.markOpened(now);
            chatRoomOpeningRepository.save(opening);
        }

        return opened;
    }
}
