package com.nidus.twinly.admin.service;

import com.nidus.twinly.admin.domain.ChatEntryState;
import com.nidus.twinly.admin.domain.ChatRoomSort;
import com.nidus.twinly.admin.dto.result.AdminChatMessagePageResult;
import com.nidus.twinly.admin.dto.result.AdminChatRoomResult;
import com.nidus.twinly.admin.dto.result.AdminPageResult;
import com.nidus.twinly.admin.reader.AdminChatRoomReader;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminChatRoomService {

    private final AdminChatRoomReader adminChatRoomReader;

    @Transactional(readOnly = true)
    public AdminPageResult<AdminChatRoomResult> search(String query, ChatEntryState entryState, ChatRoomSort sort, int page, int size) {
        return adminChatRoomReader.search(query, entryState, sort, page, size);
    }

    @Transactional(readOnly = true)
    public AdminChatRoomResult room(Long roomId) {
        return adminChatRoomReader.room(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public AdminChatMessagePageResult messages(Long roomId, Long beforeId, int size) {
        room(roomId);

        return adminChatRoomReader.messages(roomId, beforeId, size);
    }
}
