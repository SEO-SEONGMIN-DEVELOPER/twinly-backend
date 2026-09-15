package com.nidus.twinly.support;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * NICE 서버가 인증 결과를 암호화하는 규칙을 테스트에서 재현한다.
 * 가이드 3장(키 유도 → HMAC → AES-GCM)과 JAVA 샘플 코드의 역연산이다.
 * 운영 코드에는 암호화가 없으므로(복호화만 함) 테스트 전용으로 둔다.
 */
public final class NiceTestCrypto {

    public static final String TICKET = "UzEyMDI1MTExMzA5NTQ0MjI3NDE1Njc3QTM0RDBBRUZEMjI5MzUzQzEz";
    public static final int ITERATORS = 66;
    public static final String TRANSACTION_ID = "UzE0MUQyNkFDOEQ3NzYyMDIwMjUxMTEzMTAwMjM3MzM4OTQ5QUMwMkU";

    private static final SecureRandom RANDOM = new SecureRandom();

    private NiceTestCrypto() {
    }

    /** 평문 JSON 을 NICE 규격대로 암호화해 enc_data 를 만든다. IV 는 매번 난수다. */
    public static String encrypt(String plainJson, String ticket, String transactionId, int iterators) {
        try {
            byte[] aesKey = keyString(ticket, transactionId, iterators).substring(0, 32).getBytes(StandardCharsets.UTF_8);
            byte[] iv = new byte[16];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(128, iv));
            byte[] cipherText = cipher.doFinal(plainJson.getBytes(StandardCharsets.UTF_8));

            byte[] ivAndCipher = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, ivAndCipher, 0, iv.length);
            System.arraycopy(cipherText, 0, ivAndCipher, iv.length, cipherText.length);

            return Base64.getUrlEncoder().withoutPadding().encodeToString(ivAndCipher);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** enc_data 문자열에 대한 integrity_value 를 만든다. */
    public static String integrityValue(String encData, String ticket, String transactionId, int iterators) {
        try {
            byte[] hmacKey = keyString(ticket, transactionId, iterators).substring(48, 80).getBytes(StandardCharsets.UTF_8);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));

            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(encData.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 휴대폰 인증 결과 JSON. 계약된 7개 항목만 담는다. */
    public static String resultJson(String name, String birthdate, String gender, String nationalInfo,
                                    String di, String mobileCo, String mobileNo) {
        return """
                {"name":"%s","birthdate":"%s","gender":"%s","national_info":"%s","di":"%s","mobile_co":"%s","mobile_no":"%s"}
                """.formatted(name, birthdate, gender, nationalInfo, di, mobileCo, mobileNo).trim();
    }

    private static String keyString(String ticket, String transactionId, int iterators) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(ticket.toCharArray(), transactionId.getBytes(StandardCharsets.UTF_8), iterators, 512);
        byte[] derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();

        return Base64.getUrlEncoder().withoutPadding().encodeToString(derived);
    }
}
