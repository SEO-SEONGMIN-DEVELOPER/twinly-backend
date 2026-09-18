package com.nidus.twinly.user.seed;

import com.nidus.twinly.auth.config.TestVerificationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SeedTestVerificationContractTest {

    @ParameterizedTest
    @ValueSource(strings = {"prod", "stage"})
    @DisplayName("시드 계정의 전화번호와 이메일은 해당 프로필의 테스트 인증 접두사에 포함된다")
    void 시드_계정은_테스트_인증_대상이다(String profile) throws IOException {
        TestVerificationProperties properties = bind(profile);

        assertThat(UserSeeder.PHONE_PREFIX).startsWith(properties.phonePrefix());
        assertThat(UserSeeder.EMAIL_LOCAL_PREFIX).startsWith(properties.emailPrefix());
    }

    private TestVerificationProperties bind(String profile) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load(profile, new ClassPathResource("application-" + profile + ".yaml"));

        return new Binder(ConfigurationPropertySources.from(sources))
                .bindOrCreate("verification.test", TestVerificationProperties.class);
    }
}
