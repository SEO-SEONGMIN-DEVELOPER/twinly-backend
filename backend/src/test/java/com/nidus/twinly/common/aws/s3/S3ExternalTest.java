package com.nidus.twinly.common.aws.s3;

import com.nidus.twinly.common.aws.AwsConfig;
import com.nidus.twinly.common.aws.bedrock.BedrockProperties;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontProperties;
import com.nidus.twinly.common.aws.ses.SesProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("external")
@SpringBootTest(classes = {AwsConfig.class, S3Service.class})
@EnableConfigurationProperties({
        S3Properties.class,
        BedrockProperties.class,
        CloudFrontProperties.class,
        SesProperties.class
})
class S3ExternalTest {

    // 운영 자격증명에 s3:DeleteObject 권한이 없다(최소 권한).
    // 이 접두사에 걸린 버킷 라이프사이클 규칙이 만료 삭제를 대신한다.
    private static final String KEY_PREFIX = "external-test/";

    @Autowired
    S3Service s3Service;

    private final String key = KEY_PREFIX + UUID.randomUUID() + ".txt";

    @Test
    @DisplayName("없는 키를 조회하면 예외 없이 빈 값이 돌아온다")
    void head_missing_key() {
        // given: 매번 새로 만드는 UUID 키라 버킷에 존재할 수 없다

        // when: 실제 버킷에 조회를 요청한다
        Optional<Long> contentLength = s3Service.contentLength(key);

        // then: 자격증명·리전·버킷 접근이 통과했고, 404가 NoSuchKeyException으로 매핑되어 빈 값이 된다
        assertThat(contentLength).isEmpty();
    }

    @Test
    @DisplayName("실제 S3에 업로드하면 같은 크기의 오브젝트가 조회된다")
    void upload_and_head() {
        // given: 작은 더미 페이로드
        byte[] content = "probe".getBytes(StandardCharsets.UTF_8);

        // when: 실제 버킷에 업로드한다
        s3Service.upload(key, content, "text/plain");

        // then: 실제로 올라가 조회되고 크기가 일치한다 (내용이 아니라 연동 성립 여부만 본다)
        assertThat(s3Service.contentLength(key)).contains((long) content.length);
    }
}
