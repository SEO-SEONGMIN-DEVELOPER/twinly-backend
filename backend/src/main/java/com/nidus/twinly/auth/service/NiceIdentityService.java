package com.nidus.twinly.auth.service;

import com.nidus.twinly.auth.client.NiceAuthClient;
import com.nidus.twinly.auth.client.NiceAuthResultBody;
import com.nidus.twinly.auth.client.NiceAuthUrlBody;
import com.nidus.twinly.auth.client.NiceResultDecryptor;
import com.nidus.twinly.auth.client.NiceToken;
import com.nidus.twinly.auth.client.NiceTokenProvider;
import com.nidus.twinly.auth.client.NiceTokenRejectedException;
import com.nidus.twinly.auth.config.NiceProperties;
import com.nidus.twinly.auth.domain.VerifiedIdentity;
import com.nidus.twinly.auth.dto.result.IdentityAuthUrlResult;
import com.nidus.twinly.common.logging.WarnLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Function;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Service
@RequiredArgsConstructor
public class NiceIdentityService implements IdentityVerificationProvider {

    private final NiceTokenProvider niceTokenProvider;
    private final NiceAuthClient niceAuthClient;
    private final NiceResultDecryptor niceResultDecryptor;
    private final NiceProperties niceProperties;

    @Override
    public IdentityAuthUrlResult requestAuthUrl(String requestNo) {
        NiceAuthUrlBody body = withTokenRetry(requestNo, token -> niceAuthClient.requestAuthUrl(token.accessToken(), requestNo));

        return new IdentityAuthUrlResult(
                body.authUrl(),
                body.transactionId(),
                niceProperties.returnUrl(),
                niceProperties.closeUrl());
    }

    @Override
    public VerifiedIdentity fetchResult(String requestNo, String transactionId, String webTransactionId) {
        return withTokenRetry(requestNo, token -> {
            NiceAuthResultBody body = niceAuthClient.requestResult(token.accessToken(), webTransactionId, transactionId, requestNo);

            return niceResultDecryptor.decrypt(token, transactionId, body.encData(), body.integrityValue());
        }).toVerifiedIdentity();
    }

    private <T> T withTokenRetry(String requestNo, Function<NiceToken, T> call) {
        try {
            return call.apply(niceTokenProvider.getToken());
        } catch (NiceTokenRejectedException e) {
            WarnLog.log(log, "NICE 토큰이 거절되어 재발급 후 한 번 더 시도합니다.", field("requestNo", requestNo));
            niceTokenProvider.invalidate();

            return call.apply(niceTokenProvider.getToken());
        }
    }
}
