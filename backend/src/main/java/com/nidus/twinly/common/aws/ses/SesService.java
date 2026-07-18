package com.nidus.twinly.common.aws.ses;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

@Service
@RequiredArgsConstructor
public class SesService {

    private final SesClient sesClient;
    private final SesProperties sesProperties;

    public void send(String toAddress, String subject, String body) {
        /*
         * [멘토링 피드백 반영 완료]
         * 응답 성공 / 실패에 따라 에러 호출. 실패 이유도 로그에 저장하기
         * DB에 상태 저장?
         */
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
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "이메일 발송에 실패했습니다.", e);
        }
    }
}
