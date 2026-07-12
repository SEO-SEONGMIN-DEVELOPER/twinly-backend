package com.nidus.twinly.common.solapi;

import com.solapi.sdk.message.exception.SolapiEmptyResponseException;
import com.solapi.sdk.message.exception.SolapiMessageNotReceivedException;
import com.solapi.sdk.message.exception.SolapiUnknownException;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.service.DefaultMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SolapiService {

    private final DefaultMessageService defaultMessageService;
    private final SolapiProperties solapiProperties;

    public void send(String toNumber, String text) throws SolapiEmptyResponseException, SolapiUnknownException, SolapiMessageNotReceivedException {
        Message message = new Message();
        message.setFrom(solapiProperties.fromNumber());
        message.setTo(toNumber);
        message.setText(text);

        defaultMessageService.send(message);
    }
}
