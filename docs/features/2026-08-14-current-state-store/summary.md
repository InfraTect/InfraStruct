# summary: current-state-store

## 무엇을 만들었나

`com.infrastruct.internal.CurrentStateStore` — 마지막으로 apply 된 실제 상태를 JSON 으로
저장/복원하는 내부 모듈의 **뼈대(스텁)**. 공개 시그니처만 확정하고 본문은 비웠다.

| 메서드 | 계약 | 현재 동작(스텁) |
|---|---|---|
| `CurrentResources load()` | 저장된 상태 파일 → `CurrentResources` 복원 | 항상 빈 `CurrentResources` 반환 (최초 실행 = 파일 없음의 자리표시) |
| `void save(CurrentResources)` | `CurrentResources` → 상태 파일 기록 | 무동작 (예외 없이 반환) |

파이프라인에서의 자리(`InfraStruct.run()`): compare 직전 `load()`, apply 후 `save()`.

테스트 3개 추가(`CurrentStateStoreTest`), 전체 `./gradlew check` 통과.

## 왜 뼈대만인가

이번 목적은 **다른 사람이 이 타입을 import 해 호출부(InfraStruct 파이프라인, Applier)를 먼저
엮을 수 있게** 하는 것이다. `InfraStruct` 뼈대와 같은 성격이다.

실제 Gson 직렬화/역직렬화는 미해결 **설계 판단**이 걸려 있어 이번 범위에서 뺐다
(`plan.md` §4, resource-state-classes summary §7):

- `kind` 가 인터페이스(`Kind`)라 역직렬화가 `Interfaces can't be instantiated!` 로 깨진다
  → 읽는 쪽에서 `TypeAdapter<Kind>` 필요 (providerId → Kind enum → `value()` 매칭).
- 상태 클래스가 불변이라 인자 없는 생성자가 없다 → Gson 이 `Unsafe` 로 만들며 생성자의
  `copyOf` 정규화를 건너뛴다 → `InstanceCreator` 등록 또는 읽은 뒤 정규화 필요.

이 둘이 서기 전에 시그니처만 못 박아 호출부를 언블록했다. 채울 자리는 두 메서드의
`// TODO` 주석으로 표시했다.

## 위치 — `internal` (spi 아님)

`Comparator` 와 같은 `internal` 패키지. 상태를 주고받는 **그릇**(`CurrentResources` 등)만
`spi` 에 있고, 그 그릇을 다루는 **엔진 모듈**은 `internal` 에 둔다(`Comparator` 가 선례).
프로바이더·사용자 누구도 이 타입에 의존하지 않는다.

## 다음

- ~~실제 저장/복원 구현: 상태 파일 경로 결정 + Gson(`TypeAdapter<Kind>` + 불변 정규화) +
  save→load 라운드트립 테스트. 별도 feature.~~
  → ✅ 해결: [`2026-08-19-current-state-store-implementation`](../2026-08-19-current-state-store-implementation/summary.md).
  `TypeAdapter<Kind>` 대신 **전용 DTO record + `Class.forName` & `value()` 매칭** 으로 풀었다.
- ~~생성자에 provider/경로 주입은 그 설계가 선 뒤에 함께 정한다 (지금은 인자 없는 생성자).~~
  → ✅ 해결: 생성자가 `CurrentStateStore(Path stateFile)` 로 바뀌었고 **무인자 생성자는 없앴다.**
