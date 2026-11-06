package com.nidus.twinly.invite.client;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.invite.config.AppStoreProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AppleOfferSigner {

    private static final String SEPARATOR = "⁣";

    private final AppStoreProperties appStoreProperties;

    public AppleOfferSignature sign(String productId, UUID appAccountToken, Instant now) {
        if (!appStoreProperties.configured()) {
            throw new BusinessException(ErrorCode.INVITE_REWARD_APPLE_OFFER_UNAVAILABLE);
        }

        UUID nonce = UUID.randomUUID();
        long timestamp = now.toEpochMilli();
        String payload = String.join(SEPARATOR,
                appStoreProperties.bundleId(),
                appStoreProperties.keyId(),
                productId,
                appStoreProperties.offerId(),
                appAccountToken.toString().toLowerCase(Locale.ROOT),
                nonce.toString().toLowerCase(Locale.ROOT),
                String.valueOf(timestamp));

        return new AppleOfferSignature(
                productId,
                appStoreProperties.offerId(),
                appStoreProperties.keyId(),
                nonce,
                timestamp,
                appAccountToken,
                signatureOf(payload));
    }

    private String signatureOf(String payload) {
        try {
            Signature signer = Signature.getInstance("SHA256withECDSA");
            signer.initSign(AppStoreKeys.parse(appStoreProperties.privateKey()));
            signer.update(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (GeneralSecurityException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e);
        }
    }
}
