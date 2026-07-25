package com.nidus.twinly.common.solapi;

import com.solapi.sdk.message.exception.SolapiEmptyResponseException;
import com.solapi.sdk.message.exception.SolapiMessageNotReceivedException;
import com.solapi.sdk.message.exception.SolapiUnknownException;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.service.DefaultMessageService;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SolapiService {

    private final DefaultMessageService defaultMessageService;
    private final SolapiProperties solapiProperties;

    public void send(String toNumber, String text) {
        Message message = new Message();
        message.setFrom(solapiProperties.fromNumber());
        message.setTo(toNumber);
        message.setText(text);

        try {
            defaultMessageService.send(message);
        } catch (SolapiEmptyResponseException | SolapiMessageNotReceivedException | SolapiUnknownException e) {
            throw new BusinessException(ErrorCode.SMS_SEND_FAILED, e);
        }
    }
}
