package com.nidus.twinly.common.crypto;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

@Component
public class BlindIndexHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public BlindIndexHasher(CryptoProperties properties) {
        this.key = new SecretKeySpec(Base64.getDecoder().decode(properties.hmacKey()), ALGORITHM);
    }

    public String hash(String plainText) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] result = mac.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(result);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("해시 생성에 실패했습니다: ", e);
        }
    }
}