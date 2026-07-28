package com.nidus.twinly.common.aws.ses;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

@Service
@RequiredArgsConstructor
public class SesService {

    private final SesClient sesClient;
    private final SesProperties sesProperties;

    public void send(String toAddress, String subject, String body) {
        SendEmailRequest request = SendEmailRequest.builder()
                .source(sesProperties.fromAddress())
                .destination(Destination.builder().toAddresses(toAddress).build())
                .message(Message.builder()
                        .subject(Content.builder().data(subject).build())
                        .body(Body.builder().text(Content.builder().data(body).build()).build())
                        .build())
                .build();

        try {
            sesClient.sendEmail(request);
        } catch (SdkException e) {
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED, e);
        }
    }
}
