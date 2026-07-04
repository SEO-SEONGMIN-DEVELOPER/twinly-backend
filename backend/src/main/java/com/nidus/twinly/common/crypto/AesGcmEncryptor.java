package com.nidus.twinly.common.crypto;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AesGcmEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private static final String VERSION_DELIMITER = ":";

    private final Map<String, SecretKeySpec> secretKeysByVersion;
    private final String currentVersion;

    public AesGcmEncryptor(CryptoProperties properties) {
        this.secretKeysByVersion = properties.aesKeys().entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new SecretKeySpec(Base64.getDecoder().decode(entry.getValue()), "AES")
                ));
        this.currentVersion = properties.currentVersion();

        if (!secretKeysByVersion.containsKey(currentVersion)) {
            throw new IllegalStateException("현재 버전(" + currentVersion + ")에 해당하는 키가 없습니다");
        }
    }

    public String encrypt(String plaintext) throws Exception {
        SecretKeySpec currentKey = secretKeysByVersion.get(currentVersion);

        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, currentKey, new GCMParameterSpec(TAG_LENGTH, iv));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes());

        byte[] result = new byte[IV_LENGTH + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, IV_LENGTH);
        System.arraycopy(ciphertext, 0, result, IV_LENGTH, ciphertext.length);

        return currentVersion + VERSION_DELIMITER + Base64.getEncoder().encodeToString(result);
    }

    public String decrypt(String encrypted) throws Exception {
        int delimiterIndex = encrypted.indexOf(VERSION_DELIMITER);
        String version = encrypted.substring(0, delimiterIndex);
        String payload = encrypted.substring(delimiterIndex + 1);

        SecretKeySpec key = secretKeysByVersion.get(version);
        if (key == null) {
            throw new IllegalStateException("알 수 없는 암호화 버전입니다: " + version);
        }

        byte[] decoded = Base64.getDecoder().decode(payload);

        byte[] iv = new byte[IV_LENGTH];
        System.arraycopy(decoded, 0, iv, 0, IV_LENGTH);

        byte[] ciphertext = new byte[decoded.length - IV_LENGTH];
        System.arraycopy(decoded, IV_LENGTH, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));

        return new String(cipher.doFinal(ciphertext));
    }
}