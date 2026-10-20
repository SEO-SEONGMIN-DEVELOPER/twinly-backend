package com.nidus.twinly.auth.client;

import com.nidus.twinly.auth.domain.InvalidIdentityResultException;
import com.nidus.twinly.auth.domain.VerifiedIdentity;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.domain.MobileCarrier;
import com.nidus.twinly.common.domain.NationalInfo;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NiceAuthResultUnitTest {

    private static final String NAME = "홍길동";
    private static final String BIRTHDATE = "19990314";
    private static final String DI = "MC0GCCqGSIb3DQIJAyEA";
    private static final String MOBILE_NO = "01012345678";

    @Test
    @DisplayName("NICE 코드값과 yyyymmdd 생년월일을 우리 타입으로 바꿔 검증된 본인 정보로 돌려준다")
    void toVerifiedIdentity_converts_nice_codes() {
        // given: 남성(1)·내국인(0)·SKT(1) 결과
        NiceAuthResult result = new NiceAuthResult(NAME, BIRTHDATE, "1", "0", DI, "1", MOBILE_NO);

        // when
        VerifiedIdentity identity = result.toVerifiedIdentity();

        // then
        assertThat(identity.name()).isEqualTo(NAME);
        assertThat(identity.birthDate()).isEqualTo(LocalDate.of(1999, 3, 14));
        assertThat(identity.gender()).isEqualTo(Gender.MALE);
        assertThat(identity.nationalInfo()).isEqualTo(NationalInfo.DOMESTIC);
        assertThat(identity.mobileCarrier()).isEqualTo(MobileCarrier.SKT);
        assertThat(identity.phoneNumber()).isEqualTo(MOBILE_NO);
        assertThat(identity.di()).isEqualTo(DI);
    }

    @Test
    @DisplayName("필수 항목이 비어 있으면 어떤 항목이 비었는지 메시지에 담아 IDENTITY_VERIFICATION_FAILED 예외가 발생한다")
    void toVerifiedIdentity_when_required_missing_names_fields() {
        // given: 이름은 null, DI 는 공백
        NiceAuthResult result = new NiceAuthResult(null, BIRTHDATE, "1", "0", " ", "1", MOBILE_NO);

        // when & then: 값이 아닌 항목 이름만 메시지에 남는다
        assertThatThrownBy(result::toVerifiedIdentity)
                .isInstanceOf(InvalidIdentityResultException.class)
                .hasMessage("NICE 인증 결과에 필수 항목이 없습니다. missing=[name, di]")
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDENTITY_VERIFICATION_FAILED);
    }

    @Test
    @DisplayName("코드값을 해석할 수 없으면 받은 코드값을 메시지에 담아 예외가 발생한다")
    void toVerifiedIdentity_when_code_unknown_includes_codes() {
        // given: 정의되지 않은 성별 코드 9
        NiceAuthResult result = new NiceAuthResult(NAME, BIRTHDATE, "9", "0", DI, "1", MOBILE_NO);

        // when & then
        assertThatThrownBy(result::toVerifiedIdentity)
                .isInstanceOf(InvalidIdentityResultException.class)
                .hasMessage("NICE 인증 결과의 코드값을 해석할 수 없습니다. gender=9, nationalInfo=0, mobileCo=1");
    }

    @Test
    @DisplayName("생년월일이 yyyymmdd 형식이 아니면 값은 메시지에 남기지 않고, 기록용 DI 는 예외에 담는다")
    void toVerifiedIdentity_when_birthdate_invalid_hides_value() {
        // given: 하이픈이 들어간 생년월일
        NiceAuthResult result = new NiceAuthResult(NAME, "1999-03-14", "1", "0", DI, "1", MOBILE_NO);

        // when & then: 생년월일은 개인정보라 메시지에 넣지 않는다
        assertThatThrownBy(result::toVerifiedIdentity)
                .isInstanceOf(InvalidIdentityResultException.class)
                .hasMessage("NICE 인증 결과의 생년월일 형식을 해석할 수 없습니다.")
                .extracting(e -> ((InvalidIdentityResultException) e).getDi())
                .isEqualTo(DI);
    }
}
