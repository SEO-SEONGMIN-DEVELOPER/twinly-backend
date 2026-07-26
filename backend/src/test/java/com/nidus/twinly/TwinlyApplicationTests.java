package com.nidus.twinly;

import com.nidus.twinly.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * 애플리케이션 컨텍스트 스모크 테스트.
 * 풀 컨텍스트를 띄우므로 통합 테스트 하니스를 상속한다 (RANDOM_PORT + Testcontainers, @Tag("integration") 상속).
 */
class TwinlyApplicationTests extends AbstractIntegrationTest {

	@Test
	void contextLoads() {
	}

}
