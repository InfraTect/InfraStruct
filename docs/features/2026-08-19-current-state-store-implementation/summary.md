# summary: current-state-store-implementation

브랜치: `feat/CurrentStateStore-impl-v2`
선행: [`2026-08-14-current-state-store`](../2026-08-14-current-state-store/summary.md) (뼈대) — 그 문서의
"다음"에 남겨둔 **실제 저장/복원 구현**이 이번 feature 다.

## 무엇을 만들었나

| 파일 | 무엇을 |
|---|---|
| `internal/StateStoreException.java` | **신규** — 상태 파일을 읽거나 쓰지 못했을 때 던지는 런타임 예외 |
| `internal/CurrentStateStore.java` | 스텁 → **실제 구현**. 생성자가 `CurrentStateStore(Path)` 로 바뀜 |
| `spi/Kind.java` | Javadoc: "enum 으로 구현한다"(관례) → **"반드시 enum 으로 구현해야 한다"**(요구사항) + 근거 2줄 |
| `test/internal/CurrentStateStoreTest.java` | 전면 재작성 — 테스트 **36개** (전부 `@TempDir` 위에서 돈다) |
| `.gitignore` | `infras.state.json` 주석 해제 (이제 실제로 파일이 생긴다) |

TDD 하네스 기준 **행동 18개 × red→green→refactor 18 사이클**, `./gradlew check`
(Spotless + Checkstyle + SpotBugs + test + JaCoCo) 그린.

## 확정된 계약

```java
public final class CurrentStateStore {
    public static final String DEFAULT_FILE_NAME = "infras.state.json";
    public CurrentStateStore(Path stateFile);   // null 이면 NullPointerException
    public CurrentResources load();             // 파일 없으면 빈 CurrentResources
    public void save(CurrentResources resources);
}
```

**무인자 생성자는 없앴다.** 남겨두면 테스트가 레포 루트에 상태 파일을 만든다. 경로 조합은 호출부(파이프라인)의 몫이다.

상태 파일 스키마(`version: 1`)는 `spec.md` §3 그대로다. `requiredFields` 는 저장하지 않는다 —
`CurrentResourceState` 에 그 개념이 없어 읽을 때 `Set.of()` 로 채운다. `physicalId` 가 `null` 이면
JSON 에서 키가 통째로 빠진다.

## 왜 이렇게 했나 (핵심)

- **Gson 에게 상태 클래스를 절대 보여주지 않는다.** Gson 이 다루는 타입은 `private record StateFile`
  / `ResourceEntry` 두 개뿐이고, DTO ↔ `CurrentResourceState` 변환은 우리가 생성자를 호출해서 한다.
  덕분에 Gson 이 `Unsafe` 로 객체를 만들어 생성자의 `Map.copyOf`/`List.copyOf` 정규화를 건너뛰는 문제가
  아예 생기지 않는다 — 선행 feature 가 미해결로 남겼던 숙제 ②의 해법. (테스트로 못 박음:
  `roundTrippedConfigIsImmutable`, `roundTrippedDependenciesAreImmutable`)
- **`Kind` 복원은 `TypeAdapter` 가 아니라 `Class.forName` + `value()` 매칭.** 숙제 ①("인터페이스는
  인스턴스화할 수 없다")을 어댑터 등록 없이 푼다. 저장할 때 `kindType`(enum 클래스 이름)과
  `kindValue`(`Kind#value()`)를 같이 적고, 읽을 때 그 클래스의 enum 상수 중 `value()` 가 같은 것을 찾는다.
  → 복원된 `kind` 는 **원래 enum 상수 그 자체**라 프로바이더의 `switch ((AwsKind) kind)` 가 성립한다
  (`isSameAs` 로 단언).
  - 찾는 기준은 `value()` 이지 `name()` 이 아니다 (`TestKind.S3("s3-bucket")` 픽스처가 이걸 못 박는다).
  - `value()` 가 겹치면 조용히 첫 번째를 고르지 않고 예외다 — 어느 쪽을 골라도 틀릴 수 있다.
  - 저장할 때 `getClass()` 가 아니라 **`getDeclaringClass()`** 를 쓴다. 상수별 본문을 가진 enum
    (`EC2 { ... }`)은 익명 하위 클래스라 `getClass().getName()` 이 `AwsKind$1` 이 되고, 그 이름은
    `isEnum()` 이 `false` 라 다음 `load()` 에서 죽는다.
- **`Kind` 는 enum 이어야 한다가 이제 요구사항이다.** 값의 집합이 닫혀 있어야 상태 파일에서 복원할 수
  있기 때문. enum 이 아닌 `Kind` 는 `save()` 가 **파일을 건드리기 전에** 거부한다.
- **실패를 삼키는 자리는 최소한.** `load()` 에서 파일이 없을 때(= 최초 실행, 도메인 사실)만 빈 값을
  돌려주고, 나머지(읽기 IO 실패, 깨진 JSON, 빈 파일, 모르는 버전, 필수 필드 누락, `config` 값 `null`)는
  전부 `StateStoreException` 이다. 여기서 빈 값으로 뭉개면 프레임워크가 **이미 떠 있는 자원을 전부 다시
  만든다.** 모든 메시지에 상태 파일 경로가 들어간다 — 경계(`InfraStruct.run()`)가 `getMessage()` 를 그대로
  사용자에게 보여줄 수 있어야 하기 때문.
- **원자적 쓰기.** 같은 폴더의 임시 파일에 쓴 뒤 `ATOMIC_MOVE` 로 옮긴다. 쓰다 죽어도 기존 상태 파일이
  반쪽짜리로 덮이지 않는다. 임시 파일을 같은 폴더에 만드는 이유는 같은 파일시스템이어야 원자적 이동이
  성립하기 때문이고, 그래서 `AtomicMoveNotSupportedException` 폴백은 넣지 않았다.
- **`config` 숫자 정규화.** Gson 을 `LONG_OR_DOUBLE` 로 두면 `22` 가 `22.0` 이 되는 문제가 사라지고
  정수=`Long`, 소수=`Double` 로 통일된다. 값의 크기로 `int`/`long` 을 추측하지 않으므로 `int 100` 과
  `long 100` 이 같은 객체로 돌아온다. 왕복이 안정적이다(`roundTripIsStable`).

## 스펙과 달라진 점 (3가지)

1. **행동 B2/B3 의 실행 순서를 맞바꿨다.** "파일이 없으면 빈 값"(B2)은 **기존 스텁이 무조건 빈 값을
   돌려주고 있어서 RED 가 나오지 않는다.** 왕복(B3)을 먼저 구현해 `load()` 가 실제로 파일을 읽게 만든
   뒤에야 "파일 없음"이 진짜 실패 경로가 된다. 하네스의 행동 체크리스트 라벨과 각 사이클에서 쓴 테스트가
   한 칸씩 밀려 있는 이유가 이것이다 — 18개 행동은 전부 테스트로 덮였고, 18 사이클 모두 실제 RED 를 거쳤다.
2. **`save()` 에 부모 디렉터리 `null` 가드를 추가했다.** `Path#getParent()` 는 루트 경로에서 `null` 이라
   SpotBugs 가 `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` 로 잡는다. 스펙 §4.5 에 없던 분기지만 실패 계약은
   그대로다 (경로를 담은 `StateStoreException`).
3. **문제를 사라지게 하는 `catch` 가 둘이다** (스펙 §8 #4 는 하나라고 적었다): `NoSuchFileException`
   (최초 실행) 과 `deleteQuietly` 의 `IOException`. 후자는 스펙 §4.5 코드에 이미 있던 것으로, 임시 파일을
   못 지운 사실보다 **원래 실패를 그대로 보여주는 편이 낫다**는 판단이다.

## 알려진 한계 (테스트하지 않은 것 / 다음 사람이 알아야 할 것)

1. **원자적 쓰기 자체는 검증하지 않았다.** "쓰는 도중 프로세스를 죽인다"는 단위 테스트로 재현할 수 없다.
   `Files.move(..., ATOMIC_MOVE)` 를 쓰는 것으로 담보한다.
2. **쓰기 IO 실패 경로도 테스트하지 않았다.** 권한을 뺏는 테스트는 root 로 도는 CI 에서 무력화되고 OS 마다
   다르게 돈다. 읽기 쪽(`loadThrowsWhenStateFileCannotBeRead`)과 **같은 변환 규칙**을 따르는 것으로 갈음했다.
3. **⚠️ 정수는 모두 `Long` 으로 돌아온다.** JSON 에 `int`/`long` 구분이 없어 선언 타입은 보존되지 않는다.
   값은 그대로이고 `int`·`long` 어느 쪽으로 넣어도 같은 객체로 돌아오므로 `Comparator` 는 손댈 필요가 없다.
   → 대신 **`DesiredStateCreator` 가 같은 정규 타입을 써야 한다.** desired 는 파일을 거치지 않고 바로
   Comparator 로 가므로, `int port = 22` 를 그대로 오토박싱해 `Integer 22` 를 넣으면 아무것도 안 바꿨는데
   매번 변경으로 잡힌다. 스텁 Javadoc 에 명시해 두었다. (프로바이더의 `Applier` 가 만든 current 는 파일을
   왕복하며 정규화되므로 필수는 아니다.) 이 약속 자체를 구조로 없애는 방안은 `docs/plan.md` §9 참조.

   > **2026-08-20 개정.** 원래는 int 범위 정수를 `Integer` 로 좁혔고, 그 탓에 `long sizeGb = 100` 이
   > `Integer 100` 으로 돌아와 **apply 해도 사라지지 않는 유령 diff** 가 났다. `Long` 통일로 바꿔
   > `int`·`long` 양쪽을 모두 맞췄다 (plan §4.5, `CONVENTIONS.md` §9.3).
4. **`config` 의 중첩 값(객체·배열)은 정규화하지 않는다.** 스키마상 스칼라만 오기로 되어 있고, 막는 것은
   Validator 의 몫이다. 중첩 값이 오면 Gson 이 준 그대로(`LinkedTreeMap`/`ArrayList`) 들어간다.
5. **동시 접근(락), 원격 백엔드, 스키마 마이그레이션은 범위 밖이다.** 스키마가 바뀌면 `version` 을 올리고
   마이그레이션을 그때 설계한다 — 지금은 모르는 버전을 **거부**한다.

## 다음(이 브랜치 밖)

- **`InfraStruct.run()` 배선**: 상태 파일 경로를 정해(작업 디렉터리 + `DEFAULT_FILE_NAME`)
  `new CurrentStateStore(path)` 를 만들고, compare 직전에 `load()`, apply 후 `save()`.
- **경계에서의 예외 처리**: `StateStoreException.getMessage()` 를 사용자용 메시지로 바꾸는 일은
  경계(`InfraStruct.run()`)의 몫이다. 스택트레이스를 그대로 토하지 않는다.
- **Comparator**: 위 한계 3 — `config` 비교는 값 기준으로.
