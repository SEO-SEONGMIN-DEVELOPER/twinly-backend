package com.nidus.twinly.auth.client;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.support.NiceTestCrypto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static com.nidus.twinly.support.NiceTestCrypto.ITERATORS;
import static com.nidus.twinly.support.NiceTestCrypto.TICKET;
import static com.nidus.twinly.support.NiceTestCrypto.TRANSACTION_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NiceResultDecryptorUnitTest {

    private static final NiceToken TOKEN = new NiceToken("access-token", TICKET, ITERATORS, Instant.parse("2030-01-01T00:00:00Z"));
    private static final String PLAIN = NiceTestCrypto.resultJson(
            "홍길동", "19990314", "1", "0", "MC0GCCqGSIb3DQIJAyEA", "1", "01012345678");

    private final NiceResultDecryptor decryptor = new NiceResultDecryptor(JsonMapper.builder().build());

    @Test
    @DisplayName("NICE 규격(PBKDF2 → HMAC → AES-GCM)으로 암호화한 결과를 무결성 검증 후 복호화해 7개 항목을 꺼낸다")
    void decrypt_returns_result_fields() {
        // given: 같은 ticket·transaction_id·iterators 로 암호화한 enc_data 와 그 서명
        String encData = NiceTestCrypto.encrypt(PLAIN, TICKET, TRANSACTION_ID, ITERATORS);
        String integrity = NiceTestCrypto.integrityValue(encData, TICKET, TRANSACTION_ID, ITERATORS);

        // when
        NiceAuthResult result = decryptor.decrypt(TOKEN, TRANSACTION_ID, encData, integrity);

        // then
        assertThat(result.name()).isEqualTo("홍길동");
        assertThat(result.birthdate()).isEqualTo("19990314");
        assertThat(result.gender()).isEqualTo("1");
        assertThat(result.nationalInfo()).isEqualTo("0");
        assertThat(result.di()).isEqualTo("MC0GCCqGSIb3DQIJAyEA");
        assertThat(result.mobileCo()).isEqualTo("1");
        assertThat(result.mobileNo()).isEqualTo("01012345678");
    }

    @Test
    @DisplayName("같은 평문을 두 번 암호화해도 IV 가 달라 enc_data 는 다르지만 둘 다 복호화된다")
    void decrypt_handles_random_iv() {
        // given
        String first = NiceTestCrypto.encrypt(PLAIN, TICKET, TRANSACTION_ID, ITERATORS);
        String second = NiceTestCrypto.encrypt(PLAIN, TICKET, TRANSACTION_ID, ITERATORS);

        // then
        assertThat(first).isNotEqualTo(second);
        assertThat(decryptor.decrypt(TOKEN, TRANSACTION_ID, first,
                NiceTestCrypto.integrityValue(first, TICKET, TRANSACTION_ID, ITERATORS)).di()).isEqualTo("MC0GCCqGSIb3DQIJAyEA");
        assertThat(decryptor.decrypt(TOKEN, TRANSACTION_ID, second,
                NiceTestCrypto.integrityValue(second, TICKET, TRANSACTION_ID, ITERATORS)).di()).isEqualTo("MC0GCCqGSIb3DQIJAyEA");
    }

    @Test
    @DisplayName("enc_data 가 한 글자라도 바뀌면 무결성 검증에서 IDENTITY_VERIFICATION_FAILED 로 끊는다")
    void decrypt_rejects_tampered_enc_data() {
        // given: 서명은 원본 기준, 데이터는 변조
        String encData = NiceTestCrypto.encrypt(PLAIN, TICKET, TRANSACTION_ID, ITERATORS);
        String integrity = NiceTestCrypto.integrityValue(encData, TICKET, TRANSACTION_ID, ITERATORS);
        String tampered = (encData.charAt(0) == 'A' ? "B" : "A") + encData.substring(1);

        // when & then
        assertThatThrownBy(() -> decryptor.decrypt(TOKEN, TRANSACTION_ID, tampered, integrity))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDENTITY_VERIFICATION_FAILED);
    }

    @Test
    @DisplayName("integrity_value 가 다르면 복호화를 시도하지 않고 IDENTITY_VERIFICATION_FAILED 로 끊는다")
    void decrypt_rejects_wrong_integrity_value() {
        // given
        String encData = NiceTestCrypto.encrypt(PLAIN, TICKET, TRANSACTION_ID, ITERATORS);
        String wrong = NiceTestCrypto.integrityValue("other", TICKET, TRANSACTION_ID, ITERATORS);

        // when & then
        assertThatThrownBy(() -> decryptor.decrypt(TOKEN, TRANSACTION_ID, encData, wrong))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDENTITY_VERIFICATION_FAILED);
    }

    @Test
    @DisplayName("다른 토큰(ticket)으로 키를 만들면 같은 데이터도 복호화되지 않는다")
    void decrypt_with_other_ticket_fails() {
        // given: 결과 요청에 쓴 토큰과 다른 토큰의 ticket
        String encData = NiceTestCrypto.encrypt(PLAIN, TICKET, TRANSACTION_ID, ITERATORS);
        String integrity = NiceTestCrypto.integrityValue(encData, TICKET, TRANSACTION_ID, ITERATORS);
        NiceToken otherToken = new NiceToken("access-token", "other-ticket", ITERATORS, TOKEN.expiresAt());

        // when & then: HMAC 키부터 달라 무결성 검증에서 끊긴다
        assertThatThrownBy(() -> decryptor.decrypt(otherToken, TRANSACTION_ID, encData, integrity))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDENTITY_VERIFICATION_FAILED);
    }

    @Test
    @DisplayName("다른 인증 건(transaction_id)의 키로는 복호화되지 않는다")
    void decrypt_with_other_transaction_fails() {
        // given
        String encData = NiceTestCrypto.encrypt(PLAIN, TICKET, TRANSACTION_ID, ITERATORS);
        String integrity = NiceTestCrypto.integrityValue(encData, TICKET, TRANSACTION_ID, ITERATORS);

        // when & then
        assertThatThrownBy(() -> decryptor.decrypt(TOKEN, "other-transaction", encData, integrity))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDENTITY_VERIFICATION_FAILED);
    }

    @Test
    @DisplayName("enc_data 나 integrity_value 가 없으면 IDENTITY_VERIFICATION_FAILED 로 끊는다")
    void decrypt_rejects_missing_fields() {
        assertThatThrownBy(() -> decryptor.decrypt(TOKEN, TRANSACTION_ID, null, "x"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> decryptor.decrypt(TOKEN, TRANSACTION_ID, "x", null))
                .isInstanceOf(BusinessException.class);
    }
}
