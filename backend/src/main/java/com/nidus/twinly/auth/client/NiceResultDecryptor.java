package com.nidus.twinly.auth.client;

import com.nidus.twinly.common.logging.WarnLog;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Component
public class NiceResultDecryptor {

    private static final String KDF_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int KDF_KEY_BITS = 512;
    private static final int AES_KEY_BEGIN = 0;
    private static final int AES_KEY_END = 32;
    private static final int HMAC_KEY_BEGIN = 48;
    private static final int HMAC_KEY_END = 80;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String CIPHER_KEY_ALGORITHM = "AES";
    private static final int IV_LENGTH = 16;
    private static final int GCM_TAG_BITS = 128;

    private final JsonMapper jsonMapper;

    public NiceResultDecryptor(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public NiceAuthResult decrypt(NiceToken token, String transactionId, String encData, String integrityValue) {
        String keyString = deriveKeyString(token.ticket(), transactionId, token.iterators());
        byte[] aesKey = keyString.substring(AES_KEY_BEGIN, AES_KEY_END).getBytes(StandardCharsets.UTF_8);
        byte[] hmacKey = keyString.substring(HMAC_KEY_BEGIN, HMAC_KEY_END).getBytes(StandardCharsets.UTF_8);

        verifyIntegrity(transactionId, encData, integrityValue, hmacKey);

        String plain = decryptAesGcm(transactionId, encData, aesKey);

        try {
            return jsonMapper.readValue(plain, NiceAuthResult.class);
        } catch (RuntimeException e) {
            WarnLog.log(log, "NICE 인증 결과가 JSON 형식이 아닙니다.", e, field("transactionId", transactionId));
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_FAILED, e);
        }
    }

    private String deriveKeyString(String ticket, String transactionId, int iterators) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    ticket.toCharArray(), transactionId.getBytes(StandardCharsets.UTF_8), iterators, KDF_KEY_BITS);
            byte[] derived = SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(spec).getEncoded();

            return Base64.getUrlEncoder().withoutPadding().encodeToString(derived);
        } catch (GeneralSecurityException e) {
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_FAILED, e);
        }
    }

    private void verifyIntegrity(String transactionId, String encData, String integrityValue, byte[] hmacKey) {
        if (encData == null || integrityValue == null) {
            WarnLog.log(log, "NICE 인증 결과에 enc_data 또는 integrity_value 가 없습니다.", field("transactionId", transactionId));
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_FAILED);
        }

        byte[] expected;

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(hmacKey, HMAC_ALGORITHM));
            expected = mac.doFinal(encData.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_FAILED, e);
        }

        byte[] actual;

        try {
            actual = Base64.getUrlDecoder().decode(integrityValue);
        } catch (IllegalArgumentException e) {
            WarnLog.log(log, "NICE integrity_value 가 Base64Url 형식이 아닙니다.", e, field("transactionId", transactionId));
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_FAILED, e);
        }

        if (!MessageDigest.isEqual(expected, actual)) {
            WarnLog.log(log, "NICE 인증 결과의 무결성 검증에 실패해 복호화하지 않습니다.", field("transactionId", transactionId));
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_FAILED);
        }
    }

    private String decryptAesGcm(String transactionId, String encData, byte[] aesKey) {
        byte[] cipherEnc;

        try {
            cipherEnc = Base64.getUrlDecoder().decode(encData);
        } catch (IllegalArgumentException e) {
            WarnLog.log(log, "NICE enc_data 가 Base64Url 형식이 아닙니다.", e, field("transactionId", transactionId));
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_FAILED, e);
        }

        if (cipherEnc.length <= IV_LENGTH) {
            WarnLog.log(log, "NICE enc_data 길이가 IV 보다 짧습니다.", field("transactionId", transactionId), field("length", cipherEnc.length));
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_FAILED);
        }

        byte[] iv = Arrays.copyOfRange(cipherEnc, 0, IV_LENGTH);
        byte[] cipherText = Arrays.copyOfRange(cipherEnc, IV_LENGTH, cipherEnc.length);

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, CIPHER_KEY_ALGORITHM), new GCMParameterSpec(GCM_TAG_BITS, iv));

            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            WarnLog.log(log, "NICE 인증 결과 복호화에 실패했습니다. 무결성은 통과했으므로 AES 키 유도를 의심합니다.", e, field("transactionId", transactionId));
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_FAILED, e);
        }
    }
}
