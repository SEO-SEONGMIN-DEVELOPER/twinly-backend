package com.nidus.twinly.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Converter
@Component
@RequiredArgsConstructor
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final AesGcmEncryptor encryptor;

    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            return encryptor.encrypt(plainText);
        } catch (Exception e) {
            throw new IllegalStateException("암호화에 실패했습니다: ", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String cipherText) {
        if (cipherText == null) {
            return null;
        }
        try {
            return encryptor.decrypt(cipherText);
        } catch (Exception e) {
            throw new IllegalStateException("복호화에 실패했습니다: ", e);
        }
    }
}