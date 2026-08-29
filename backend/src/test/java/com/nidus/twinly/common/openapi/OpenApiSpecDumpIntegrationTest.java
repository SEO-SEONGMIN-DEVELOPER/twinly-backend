package com.nidus.twinly.common.openapi;

import com.nidus.twinly.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 배포 대상 커밋의 OpenAPI 스펙을 파일로 남긴다.
 * CD 가 이 파일을 받아 Apidog 에 올리므로, 운영 서버에 문서 엔드포인트를 열지 않고도
 * "배포되는 그 커밋의 스펙"을 보장할 수 있다.
 */
@TestPropertySource(properties = "springdoc.api-docs.enabled=true")
class OpenApiSpecDumpIntegrationTest extends AbstractIntegrationTest {

    private static final Path OUTPUT = Path.of("build", "openapi", "openapi.json");

    @Test
    @DisplayName("OpenAPI 스펙을 생성해 CD 가 집어갈 위치에 저장한다")
    void dumps_openapi_spec() throws Exception {
        // given: springdoc 이 켜진 컨텍스트

        // when: OpenAPI 문서 조회
        var result = mockMvc.perform(get("/docs/openapi").accept(MediaType.APPLICATION_JSON));

        // then: 문서 생성이 깨지지 않았는지 먼저 확인한다 (빈 스펙이 올라가면 Apidog 에서 전량 삭제된다)
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths").isNotEmpty());

        String spec = result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(spec).doesNotContain("-controller");

        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT, spec, StandardCharsets.UTF_8);
    }
}
