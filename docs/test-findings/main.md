# main 도메인 테스트 발견사항

## 테스트 통과 여부 (springdoc 스펙 기준, 이번 라운드 최종)

| 구분 | 결과 |
|---|---|
| 단위/슬라이스 (`MainControllerUnitTest` 3 + `MainServiceUnitTest` 4) | **7/7 통과** |
| 통합 (`MainIntegrationTest`) | **3/3 통과** |

springdoc이 노출하는 이 도메인의 오퍼레이션 1개는 단위·통합 양쪽 모두 커버되어 있다.
**이번 라운드에서 새로 확인된 운영 코드 버그는 없다.** 아래 발견사항은 이전 라운드의 기록이며 여전히 유효하다.

---

## 테스트 작성 시 메모 (버그 아님)

- `Season`은 정적 팩토리·세터가 없어 테스트에서 인스턴스를 만들 수 없다. 단위 테스트는 `BeanUtils.instantiateClass(Season.class)` + `ReflectionTestUtils.setField`로, 통합 테스트는 `id`를 설정값(`app.current-season-id=1`)에 맞춰야 해서 `JdbcTemplate`으로 직접 INSERT 했다(`id`가 `AUTO_INCREMENT`라 JPA로는 id를 지정할 수 없다).
- 통합 테스트의 진행률 검증은 정수 나눗셈 경계에서 흔들리지 않도록 시즌 구간을 중앙에서 살짝 비껴(총 200일 중 101일 경과 → 50%) 잡았다.

---

**남은 항목 없음.** 기록돼 있던 발견사항은 모두 처리되었거나 판단으로 닫혔다. 이력은 [_summary.md](_summary.md) 참조.
