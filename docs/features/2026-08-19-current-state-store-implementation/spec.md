# spec: current-state-store-implementation

`plan.md` 를 테스트 가능한 **행동 목록**으로 옮긴 것. §5 의 `- ` 불릿들이 첫 `/red` 때
체크리스트(`state.json` 의 `behaviors[]`)로 등록되고, 위에서부터 순서대로 red→green→refactor 를 돈다.

> ⚠️ **이 문서에서 `- ` 로 시작하는 줄은 §5 에만 있다.** 하네스가 문서 전체에서 `- ` 불릿을 긁어
> 행동으로 등록하기 때문에, 다른 절은 전부 번호 목록·표·코드블록으로 쓴다.

이 feature 는 스텁이 아니라 **실제 구현**이다. 따라서 행동은 "왕복이 성립하는가"(§5 B2~B11)와
"실패했을 때 조용히 넘어가지 않는가"(§5 B12~B18) 두 축으로 나뉜다.

---

## 1. 산출물 — 이번에 손대는 파일

| 파일 | 무엇을 | 근거 |
|---|---|---|
| `internal/StateStoreException.java` | **신규** 생성 | plan §4.4 |
| `internal/CurrentStateStore.java` | 본문 구현 + 생성자를 `CurrentStateStore(Path)` 로 교체 | plan §4.2 |
| `spi/Kind.java` | Javadoc 에 "**enum 으로 구현해야 한다**" 를 관례가 아니라 **요구사항**으로 명시 | plan §4.1 |
| `test/internal/CurrentStateStoreTest.java` | **전면 재작성** (기존 3개는 무인자 생성자·스텁 계약을 검증하므로 계약째로 사라진다) | plan §5 |
| `.gitignore` | `# infras.state.json` 주석 해제 | plan §5 |

작업 순서 메모(행동 순서와는 별개):

1. `StateStoreException` 은 B1 green 에서 함께 만든다 — 그것 없이는 B12 이후를 컴파일할 수 없다.
2. `Kind` Javadoc 수정은 B18(enum 아닌 Kind 거부) green 에서 같이 넣고, 문구는 `/doc` 에서 다듬는다.
3. `.gitignore` 주석 해제는 B3(첫 실제 저장) green 에서 — 그 시점부터 실제로 파일이 생긴다.
4. `CONVENTIONS.md` §8·§9 는 **이 브랜치에서 건드리지 않는다** (plan §4 머리말 — 별도 브랜치의 PR).

---

## 2. 공개 인터페이스 시그니처 (확정)

### 2.1 `com.infrastruct.internal.StateStoreException`

`internal/ResourceScanException.java`(`feat/resource-scanner-skeleton`)와 **같은 모양**이다.
차이는 메시지에 담는 것이 FQCN 이 아니라 **상태 파일 경로**라는 점뿐이다.

```java
package com.infrastruct.internal;

/**
 * 상태 파일을 읽거나 쓰지 못했을 때 던진다.
 *
 * <p>RuntimeException 인 이유 / 메시지에 파일 경로를 담는 이유는 Javadoc 본문에 적는다(plan §4.4).
 */
public class StateStoreException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StateStoreException(String message) {
        super(message);
    }

    public StateStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

`final` 이 아닌 이유: 나중에 "깨진 JSON"과 "IO 실패"를 하위 타입으로 쪼갤 여지를 남긴다(plan §4.4).

### 2.2 `com.infrastruct.internal.CurrentStateStore`

```java
public final class CurrentStateStore {

    /** 상태 파일의 관례적 이름. 경로 조합은 호출부(파이프라인)의 몫이다. */
    public static final String DEFAULT_FILE_NAME = "infras.state.json";

    /** 이 구현이 읽고 쓸 수 있는 스키마 버전. */
    private static final int SCHEMA_VERSION = 1;

    private final Path stateFile;

    /**
     * @param stateFile 읽고 쓸 상태 파일 경로 (존재하지 않아도 된다)
     * @throws NullPointerException stateFile 이 null 인 경우
     */
    public CurrentStateStore(Path stateFile);

    /**
     * @return 복원된 현재 상태. 파일이 없으면 원소 없는 CurrentResources
     * @throws StateStoreException 파일을 읽을 수 없거나, 내용이 이 구현이 아는 스키마가 아닌 경우
     */
    public CurrentResources load();

    /**
     * @param resources 저장할 현재 상태
     * @throws NullPointerException resources 가 null 인 경우
     * @throws StateStoreException 저장할 수 없는 상태이거나(enum 아닌 Kind) 파일을 쓰지 못한 경우
     */
    public void save(CurrentResources resources);
}
```

**무인자 생성자는 없앤다.** 남겨두면 테스트가 레포 루트에 상태 파일을 만들게 된다(plan §4.2).

### 2.3 파일 스키마 전용 DTO — `CurrentStateStore` 안의 `private static record`

**Gson 에게 `CurrentResourceState` 를 절대 보여주지 않는다**(plan §2 두 번째 숙제). Gson 이 다루는
타입은 아래 두 record 뿐이고, DTO ↔ 상태 클래스 변환은 우리가 생성자를 호출해서 한다.

```java
/** 상태 파일 전체. */
private record StateFile(int version, List<ResourceEntry> resources) {}

/** 자원 하나. 필드 이름이 곧 JSON 키다. */
private record ResourceEntry(
        String kindType,
        String kindValue,
        String logicalId,
        String physicalId,
        Map<String, Object> config,
        List<String> dependencies) {}
```

메모 3가지:

1. Gson 2.10+ 는 **record 를 canonical 생성자로 만든다** → `Unsafe` 경로를 타지 않는다.
   빠진 컴포넌트는 기본값으로 채워진다(`int` → `0`, 참조형 → `null`). 그래서 `version` 이 없는
   파일은 `version=0` 이 되어 §4.3 의 버전 검사에 걸린다.
2. `private` 중첩 record 라 파일 스키마가 밖으로 새지 않는다. Checkstyle `OneTopLevelClass` 는
   top-level 만 보므로 중첩은 문제없다.
3. 이 DTO 는 **테스트에서 직접 쓰지 않는다.** 테스트는 공개 API(`save`/`load`)와 생 JSON 문자열만 쓴다.

### 2.4 Gson 인스턴스 (확정)

```java
private static final Gson GSON =
        new GsonBuilder()
                .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
                .setPrettyPrinting()
                .create();
```

| 설정 | 왜 |
|---|---|
| `LONG_OR_DOUBLE` | 기본값(`DOUBLE`)이면 `Object` 자리의 `22` 가 `22.0` 으로 온다. 이 정책은 소수부가 없으면 `Long`, 있으면 `Double` 로 준다 → plan §4.5 정규화 규칙의 절반을 Gson 이 해준다. 나머지 절반(`Long` → int 범위면 `Integer`)만 우리가 한다 |
| `setPrettyPrinting` | 상태 파일은 **사람이 읽는 물건**이다(plan §4.2). 대신 테스트는 문자열 매칭이 아니라 `JsonParser` 로 파싱해 검증한다 — 포맷이 바뀌어도 안 깨지게 |
| `serializeNulls` 안 씀 | 기본값(null 필드 생략)이라 `physicalId` 가 null 이면 JSON 에서 키가 통째로 빠진다(plan §3) |

### 2.5 `spi/Kind.java` Javadoc 변경 (구현 대상 아님, 문서)

기존: *"프로바이더가 자신의 자원 목록을 enum 으로 구현한다"* (관례로 읽힘)
이번: **"반드시 enum 으로 구현해야 한다"** + 근거 두 줄
(① 상태 파일에서 복원하려면 값의 집합이 닫혀 있어야 한다, ② enum 이 아니면
`CurrentStateStore` 가 `StateStoreException` 을 던져 저장 자체가 실패한다).

---

## 3. 상태 파일 스키마

```json
{
  "version": 1,
  "resources": [
    {
      "kindType": "com.example.aws.AwsKind",
      "kindValue": "EC2",
      "logicalId": "ec2.myEc2",
      "physicalId": "i-0abc123",
      "config": {
        "instanceType": "t3.micro",
        "port": 22
      },
      "dependencies": [
        "vpc.myVpc"
      ]
    }
  ]
}
```

| 키 | 타입 | 필수 | 비고 |
|---|---|---|---|
| `version` | int | ✅ | 항상 `1`. 다르면 예외 (§4.3) |
| `resources` | 배열 | ✅ | 비어 있어도 된다. 순서 = 저장 시 순서 |
| `kindType` | string | ✅ | enum 클래스의 **`Class#getName()`** 형식. 중첩 enum 은 `Outer$Inner` |
| `kindValue` | string | ✅ | `Kind#value()` 의 값. `name()` 이 아니다 (plan §4.1) |
| `logicalId` | string | ✅ | |
| `physicalId` | string | ❌ | apply 전이면 키가 아예 없다 |
| `config` | 객체 | ✅ | 비어 있어도 된다. 값은 스칼라 |
| `dependencies` | 배열 | ✅ | 비어 있어도 된다 |

`requiredFields` 는 **저장하지 않는다.** `CurrentResourceState` 에는 그 개념이 없어 항상 비어 있고,
읽을 때 `Set.of()` 로 채운다(plan §3).

---

## 4. 동작 계약 (구현이 그대로 따라야 할 흐름)

### 4.1 `load()`

```java
public CurrentResources load() {
    String json;
    try {
        json = Files.readString(stateFile, StandardCharsets.UTF_8);
    } catch (NoSuchFileException ignored) {
        return new CurrentResources(List.of());          // 최초 실행 = 도메인 사실, 유일하게 삼키는 자리
    } catch (IOException e) {
        throw new StateStoreException("상태 파일을 읽지 못했다: " + stateFile, e);
    }

    StateFile parsed;
    try {
        parsed = GSON.fromJson(json, StateFile.class);
    } catch (JsonParseException e) {                      // JsonSyntaxException 의 상위 타입
        throw new StateStoreException("상태 파일의 JSON 을 해석하지 못했다: " + stateFile, e);
    }
    if (parsed == null) {                                 // 빈 파일 / "null" 리터럴
        throw new StateStoreException("상태 파일이 비어 있다: " + stateFile);
    }
    if (parsed.version() != SCHEMA_VERSION) {
        throw new StateStoreException(
                "모르는 상태 파일 버전이다(version=" + parsed.version()
                        + ", 지원=" + SCHEMA_VERSION + "): " + stateFile);
    }

    List<ResourceEntry> entries = requireField(parsed.resources(), "resources");
    List<CurrentResourceState> states = new ArrayList<>(entries.size());
    for (ResourceEntry entry : entries) {
        states.add(toState(entry));
    }
    return new CurrentResources(states);
}
```

### 4.2 DTO → 상태 클래스 (`toState`)

```java
private CurrentResourceState toState(ResourceEntry entry) {
    requireField(entry, "resources[]");
    String kindType = requireField(entry.kindType(), "kindType");
    String kindValue = requireField(entry.kindValue(), "kindValue");
    String logicalId = requireField(entry.logicalId(), "logicalId");
    Map<String, Object> config = requireField(entry.config(), "config");
    List<String> dependencies = requireField(entry.dependencies(), "dependencies");

    return new CurrentResourceState(
            restoreKind(kindType, kindValue),
            logicalId,
            normalizeConfig(config),
            dependencies,
            Set.of(),                    // CurrentResourceState 는 requiredFields 를 쓰지 않는다
            entry.physicalId());         // null 허용
}

/** 없거나 null 이면 StateStoreException. 메시지에 필드 이름과 파일 경로를 담는다. */
private <T> T requireField(T value, String name) {
    if (value == null) {
        throw new StateStoreException("상태 파일 항목에 '" + name + "' 가 없다: " + stateFile);
    }
    return value;
}
```

생성자에 넘기는 순간 `ResourceState` 의 `Map.copyOf` / `List.copyOf` / `Set.copyOf` 가 돌아
**불변 정규화가 되살아난다** — 이것이 "Gson 에게 상태 클래스를 맡기지 않는다"의 목적이다(plan §2).

### 4.3 `kind` 복원 (`restoreKind`) — plan §4.1 A안

```java
private Kind restoreKind(String kindType, String kindValue) {
    Class<?> type;
    try {
        type = Class.forName(kindType);
    } catch (ClassNotFoundException e) {
        throw new StateStoreException(
                "kindType 클래스를 찾을 수 없다(" + kindType + "): " + stateFile, e);
    }
    if (!type.isEnum() || !Kind.class.isAssignableFrom(type)) {
        throw new StateStoreException(
                "kindType 이 Kind 를 구현한 enum 이 아니다(" + kindType + "): " + stateFile);
    }

    Kind found = null;
    for (Object constant : type.getEnumConstants()) {
        Kind candidate = (Kind) constant;
        if (kindValue.equals(candidate.value())) {
            if (found != null) {
                throw new StateStoreException(
                        "kindValue 가 여러 enum 상수와 겹친다("
                                + kindType + "." + kindValue + "): " + stateFile);
            }
            found = candidate;
        }
    }
    if (found == null) {
        throw new StateStoreException(
                "kindValue 에 해당하는 enum 상수가 없다("
                        + kindType + "." + kindValue + "): " + stateFile);
    }
    return found;
}
```

두 가지 함정을 미리 못 박는다:

1. **찾는 기준은 `value()` 이지 `name()` 이 아니다.** `value()` 만이 `Kind` 인터페이스의 계약이다(plan §4.1).
2. **중복은 조용히 첫 번째를 고르지 않는다.** 어느 쪽을 골라도 틀릴 수 있으므로 예외다.

### 4.4 `config` 값 정규화 (`normalizeConfig`) — plan §4.5

Gson 이 `LONG_OR_DOUBLE` 로 `Long`/`Double` 까지 만들어 준 상태에서, **`Long` 중 int 범위인 것만
`Integer` 로 좁힌다.**

```java
private Map<String, Object> normalizeConfig(Map<String, Object> raw) {
    Map<String, Object> normalized = new LinkedHashMap<>(raw.size());
    for (Map.Entry<String, Object> e : raw.entrySet()) {
        Object value = e.getValue();
        if (value == null) {
            throw new StateStoreException(
                    "config 값이 null 이다(key=" + e.getKey() + "): " + stateFile);
        }
        if (value instanceof Long l && l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
            value = l.intValue();
        }
        normalized.put(e.getKey(), value);
    }
    return normalized;
}
```

| JSON | Gson(LONG_OR_DOUBLE) | 정규화 후 |
|---|---|---|
| `22` | `Long 22` | **`Integer 22`** |
| `3000000000` | `Long 3000000000` | `Long 3000000000` |
| `22.5` | `Double 22.5` | `Double 22.5` |
| `"t3.micro"` | `String` | 그대로 |
| `true` | `Boolean` | 그대로 |
| `null` | `null` | **예외** (`Map.copyOf` 가 맥락 없는 NPE 를 던지기 전에 우리가 먼저 막는다) |

`Integer` 를 우선하는 이유와 남는 한계(`long sizeGb = 100` → `Integer 100`)는 plan §4.5 그대로다.
스칼라가 아닌 값(중첩 객체·배열)은 **정규화하지 않고 Gson 이 준 그대로 넣는다** — §7 한계 참조.

### 4.5 `save()` — 원자적 쓰기 (plan §4.4)

```java
public void save(CurrentResources resources) {
    Objects.requireNonNull(resources, "resources");

    List<ResourceEntry> entries = resources.resources().stream().map(this::toEntry).toList();
    String json = GSON.toJson(new StateFile(SCHEMA_VERSION, entries));

    Path target = stateFile.toAbsolutePath();
    Path directory = target.getParent();
    Path temp = null;
    try {
        Files.createDirectories(directory);
        temp = Files.createTempFile(directory, "infras.state", ".tmp");   // 같은 폴더여야 ATOMIC_MOVE 가 된다
        Files.writeString(temp, json, StandardCharsets.UTF_8);
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        temp = null;                                                       // 옮겼으니 지울 것이 없다
    } catch (IOException e) {
        throw new StateStoreException("상태 파일을 저장하지 못했다: " + stateFile, e);
    } finally {
        deleteQuietly(temp);                                               // 실패 시 임시 파일을 남기지 않는다
    }
}
```

`ATOMIC_MOVE` 는 다른 옵션을 무시하므로 `REPLACE_EXISTING` 을 같이 주지 않는다(이미 덮어쓴다).
`AtomicMoveNotSupportedException` 대비 폴백은 **넣지 않는다** — 임시 파일이 대상과 같은 폴더라
같은 파일시스템이 보장된다.

### 4.6 상태 클래스 → DTO (`toEntry`)

```java
private ResourceEntry toEntry(CurrentResourceState state) {
    Kind kind = state.kind();
    if (!(kind instanceof Enum<?> constant)) {
        throw new StateStoreException(
                "Kind 는 enum 이어야 저장할 수 있다("
                        + (kind == null ? "null" : kind.getClass().getName())
                        + ", logicalId=" + state.logicalId() + "): " + stateFile);
    }
    return new ResourceEntry(
            constant.getDeclaringClass().getName(),   // getClass() 가 아니다 ↓
            kind.value(),
            state.logicalId(),
            state.physicalId(),
            state.config(),
            state.dependencies());
}
```

⚠️ **`getClass()` 가 아니라 `getDeclaringClass()` 를 쓴다.** 상수별 본문을 가진 enum
(`EC2 { ... }`)은 익명 하위 클래스라 `getClass().getName()` 이 `AwsKind$1` 이 되고,
그 이름은 `isEnum()` 이 `false` 라 다음 `load()` 에서 §4.3 에 걸려 죽는다.

### 4.7 실패 계약 요약표

| 상황 | 행동 | 메시지에 담는 것 |
|---|---|---|
| 파일 없음 (최초 실행) | 빈 `CurrentResources` | (예외 아님) |
| 읽기 IO 실패 | `StateStoreException` | 파일 경로 + `cause` |
| JSON 문법 오류 / 루트가 객체가 아님 | `StateStoreException` | 파일 경로 + `cause` |
| 빈 파일 | `StateStoreException` | 파일 경로 |
| `version != 1` (없으면 `0`) | `StateStoreException` | 발견된 버전 + 파일 경로 |
| 필수 필드 누락·`null` | `StateStoreException` | 필드 이름 + 파일 경로 |
| `config` 값이 `null` | `StateStoreException` | 키 이름 + 파일 경로 |
| `kindType` 클래스 없음 | `StateStoreException` | `kindType` + 파일 경로 + `cause` |
| `kindType` 이 enum 아님 / `Kind` 미구현 | `StateStoreException` | `kindType` + 파일 경로 |
| `kindValue` 매칭 상수 없음 / 둘 이상 | `StateStoreException` | `kindType.kindValue` + 파일 경로 |
| `save` 대상 kind 가 enum 아님 | `StateStoreException` | 실제 클래스명 + logicalId + 파일 경로 |
| 쓰기 IO 실패 | `StateStoreException`, 기존 파일 보존 | 파일 경로 + `cause` |

**모든 메시지에 상태 파일 경로가 들어간다** — 경계(`InfraStruct.run()`)가 `getMessage()` 를 그대로
사용자에게 보여줄 수 있어야 하기 때문이다(plan §4.4 의 보장 2).

---

## 5. 행동 목록 (red 사이클 순서)

- CurrentStateStore 는 상태 파일 경로를 받아 만들어지고 경로가 null 이면 거부한다
- 상태 파일이 없으면 load() 가 빈 CurrentResources 를 돌려준다
- save() 한 자원을 load() 하면 모든 필드가 그대로 돌아온다
- 왕복한 kind 는 원래 enum 상수 그 자체다
- physicalId 가 null 인 자원도 왕복하고 저장된 JSON 에는 그 키가 없다
- config 의 숫자는 Integer/Long/Double 로 정규화되어 돌아온다
- 왕복한 config 와 dependencies 는 불변이고 requiredFields 는 비어 있다
- 자원이 여럿이면 저장 순서 그대로 복원되고 빈 목록도 왕복한다
- save() 를 다시 하면 마지막 상태만 남는다
- 부모 디렉터리가 없으면 save() 가 만들어 준다
- save() 가 쓴 파일은 version 1 스키마를 그대로 담는다
- JSON 이 깨진 상태 파일을 load() 하면 StateStoreException 이 나고 메시지에 파일 경로가 있다
- version 이 1 이 아니면 load() 가 StateStoreException 을 던진다
- kindType 을 Kind 구현 enum 으로 로드할 수 없으면 load() 가 StateStoreException 을 던진다
- kindValue 로 enum 상수를 하나로 특정할 수 없으면 load() 가 StateStoreException 을 던진다
- 항목의 필수 필드가 없거나 config 값이 null 이면 load() 가 StateStoreException 을 던진다
- 상태 파일을 읽을 수 없으면 load() 가 StateStoreException 을 던진다
- enum 이 아닌 Kind 를 save() 하면 StateStoreException 이 난다

---

## 6. 테스트 설계 (Given / When / Then)

전부 `framework/src/test/java/com/infrastruct/internal/CurrentStateStoreTest.java` 한 파일.
**모든 테스트는 `@TempDir` 위에서 돈다** — 레포 루트를 더럽히지 않기 위해서다(plan §4.2).

### 6.0 공통 픽스처

```java
package com.infrastruct.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentStateStoreTest {

    /** 픽스처: 프로바이더가 Kind 를 enum 으로 구현하는 방식을 흉내 낸다. */
    enum TestKind implements Kind {
        EC2("EC2"),
        VPC("VPC"),
        S3("s3-bucket");   // name() 과 value() 가 다른 상수 — §6.4 #2 가 쓴다

        private final String value;

        TestKind(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }

    /** 픽스처: value() 가 겹치는 enum — 상수를 하나로 특정할 수 없는 경우. */
    enum DuplicateValueKind implements Kind {
        FIRST,
        SECOND;

        @Override
        public String value() {
            return "SAME";
        }
    }

    /** 픽스처: enum 이 아닌 Kind 구현 — 상태로 저장할 수 없다. */
    static final class NotEnumKind implements Kind {
        @Override
        public String value() {
            return "NOT_ENUM";
        }
    }

    @TempDir Path tempDir;

    private Path stateFile;
    private CurrentStateStore store;

    @BeforeEach
    void setUp() {
        stateFile = tempDir.resolve(CurrentStateStore.DEFAULT_FILE_NAME);
        store = new CurrentStateStore(stateFile);
    }

    /** 자원 하나짜리 픽스처. */
    private static CurrentResourceState resource(String logicalId, String physicalId) {
        return new CurrentResourceState(
                TestKind.EC2,
                logicalId,
                Map.of("instanceType", "t3.micro", "port", 22, "enabled", true),
                List.of("vpc.myVpc"),
                Set.of(),
                physicalId);
    }

    private static CurrentResources resources(CurrentResourceState... states) {
        return new CurrentResources(List.of(states));
    }

    /** 손으로 만든 상태 파일을 그대로 심는다 (실패 계약 테스트용). */
    private void writeStateFile(String json) throws IOException {
        Files.writeString(stateFile, json, StandardCharsets.UTF_8);
    }

    /** 자원 항목 하나짜리 상태 파일 JSON 을 만든다. 인자를 비틀어 각 실패 상황을 만든다. */
    private static String stateJson(int version, String entryJson) {
        return "{\"version\":" + version + ",\"resources\":[" + entryJson + "]}";
    }

    private static String entryJson(String kindType, String kindValue) {
        return "{\"kindType\":\"" + kindType + "\",\"kindValue\":\"" + kindValue + "\","
                + "\"logicalId\":\"ec2.myEc2\",\"config\":{},\"dependencies\":[]}";
    }
}
```

`kindType` 문자열은 **하드코딩하지 말고** `TestKind.class.getName()` 으로 만든다 — 테스트 클래스를
옮기거나 이름을 바꿔도 안 깨지고, 중첩 클래스의 `$` 표기를 손으로 틀릴 일도 없다.

### 6.1 B1 · 경로를 받는 생성자

| # | 테스트 | Given | When | Then |
|---|---|---|---|---|
| 1 | `holdsTheStateFilePathGivenToTheConstructor` | `@TempDir` 안의 경로 | `new CurrentStateStore(path)` | 예외 없이 만들어진다 |
| 2 | `rejectsNullStateFilePath` | 없음 | `new CurrentStateStore(null)` | `NullPointerException` |
| 3 | `defaultFileNameIsInfrasStateJson` | 없음 | `CurrentStateStore.DEFAULT_FILE_NAME` 을 읽는다 | `"infras.state.json"` |

> #1 은 "무인자 생성자가 사라졌다"를 **컴파일**로 증명한다 — `new CurrentStateStore()` 를 쓰는 코드는
> 더 이상 컴파일되지 않는다. 별도 테스트로 만들 수 없는 종류의 계약이다.

### 6.2 B2 · 파일이 없을 때

```
Given  상태 파일이 아직 없다 (@TempDir 는 비어 있다)
When   store.load()
Then   null 이 아니고, resources() 가 비어 있다
```

테스트: `loadReturnsEmptyResourcesWhenStateFileDoesNotExist`
추가 단언: 파일이 **생기지 않는다** (`Files.exists(stateFile)` 가 `false`) — `load()` 는 읽기 전용이다.

### 6.3 B3 · 왕복이 모든 필드를 보존한다 (이 feature 의 본질)

```
Given  kind=TestKind.EC2, logicalId="ec2.myEc2", physicalId="i-0abc123",
       config={instanceType:"t3.micro", port:22, enabled:true},
       dependencies=["vpc.myVpc"] 인 CurrentResourceState 하나
When   store.save(resources) 한 뒤 store.load()
Then   자원이 1개이고, logicalId·physicalId·config·dependencies 가 저장 전과 같다
```

테스트: `roundTripPreservesEveryField`
`config` 는 `containsExactlyInAnyOrderEntriesOf(...)` 로 통째로 비교한다 (문자열·불리언·정수가
한 번에 검증된다). 숫자 타입 자체는 B6 에서 따로 본다.

### 6.4 B4 · kind 가 원래 enum 상수 그 자체다 (plan §4.1 A안의 증명)

```
Given  kind 가 TestKind.EC2 인 자원
When   save → load
Then   복원된 kind 가 TestKind.EC2 와 같은 인스턴스다
```

테스트: `roundTripRestoresTheSameEnumConstant`
단언: `assertThat(loaded.kind()).isSameAs(TestKind.EC2);`
**`isEqualTo` 가 아니라 `isSameAs`** 여야 한다 — 프로바이더의 `switch ((AwsKind) kind)` 캐스팅이
성립한다는 것을 보이는 것이 목적이다(plan §4.1).

추가 케이스 `roundTripUsesValueNotEnumConstantName`:
```
Given  value() 가 상수 이름과 다른 Kind (예: TestKind 에 값이 "vpc-v2" 인 상수를 하나 더 둔다)
When   save → load
Then   그 상수가 그대로 복원된다  ← name() 으로 찾는 구현이면 실패한다
```
> 이 케이스가 쓰는 것이 §6.0 픽스처의 `S3("s3-bucket")` 상수다 —
> `name()` 은 `S3`, `value()` 는 `s3-bucket` 이라 둘을 혼동한 구현이 바로 드러난다.

### 6.5 B5 · physicalId 가 null 인 자원

| # | 테스트 | Given | When | Then |
|---|---|---|---|---|
| 1 | `roundTripPreservesNullPhysicalId` | `physicalId=null` 인 자원 | save → load | 복원된 `physicalId()` 가 `null` |
| 2 | `physicalIdKeyIsAbsentWhenNull` | 같음 | save 후 파일을 `JsonParser` 로 판다 | 항목 객체에 `physicalId` 키가 없다 |

### 6.6 B6 · config 숫자 정규화 (plan §4.5)

```
Given  config = { small: 22 (int), big: 3_000_000_000L (long), ratio: 0.5 (double) }
When   save → load
Then   small 은 Integer 22, big 은 Long 3000000000, ratio 는 Double 0.5
```

테스트 3개로 쪼갠다 — 실패했을 때 어느 규칙이 깨졌는지 바로 보이게:

| # | 테스트 | 단언 |
|---|---|---|
| 1 | `smallIntegerStaysInteger` | `assertThat(config.get("small")).isEqualTo(22).isInstanceOf(Integer.class)` |
| 2 | `numberBeyondIntRangeBecomesLong` | `isEqualTo(3_000_000_000L).isInstanceOf(Long.class)` |
| 3 | `decimalStaysDouble` | `isEqualTo(0.5).isInstanceOf(Double.class)` |

> ⚠️ `isEqualTo(22)` 만으로는 부족하다 — AssertJ 의 숫자 비교는 타입을 눈감아 줄 수 있으므로
> **`isInstanceOf` 를 반드시 함께 건다.** 이 테스트가 없으면 `22.0` 회귀를 잡지 못한다.

추가 케이스 `roundTripIsStable`: `save → load → save → load` 를 두 번 돌려 두 번째 결과가
첫 번째와 같은지 본다. plan §4.5 의 "왕복이 **안정적**이다" 보장 그 자체다.

### 6.7 B7 · 불변성과 requiredFields (plan §2 두 번째 숙제의 증명)

| # | 테스트 | When | Then |
|---|---|---|---|
| 1 | `roundTrippedConfigIsImmutable` | `loaded.config().put("x", 1)` | `UnsupportedOperationException` |
| 2 | `roundTrippedDependenciesAreImmutable` | `loaded.dependencies().add("x")` | `UnsupportedOperationException` |
| 3 | `requiredFieldsAreEmptyAfterRoundTrip` | save → load | `requiredFields()` 가 비어 있다 |

1·2 는 `assertThatThrownBy(...).isInstanceOf(UnsupportedOperationException.class)` 로 쓴다.
Gson 이 `Unsafe` 로 객체를 만들었다면 `copyOf` 가 안 돌아 여기서 잡힌다.

### 6.8 B8 · 여러 자원과 빈 목록

| # | 테스트 | Given | Then |
|---|---|---|---|
| 1 | `preservesResourceOrder` | logicalId 가 `a`, `b`, `c` 인 자원 3개 | 복원된 `logicalId()` 들이 `a, b, c` 순서 그대로 (`containsExactly`) |
| 2 | `emptyResourcesRoundTrips` | `new CurrentResources(List.of())` | save 후 파일이 존재하고, load 결과가 비어 있다 |

> #2 가 중요한 이유: "빈 상태를 저장했다"와 "저장한 적이 없다"는 다른 사건인데, 파일이 생기지
> 않으면 둘을 구분할 수 없다.

### 6.9 B9 · 덮어쓰기

```
Given  자원 a 하나를 save() 했다
When   자원 b 하나만 담아 다시 save() 하고 load()
Then   자원이 1개이고 logicalId 가 "b" 다 (a 는 남아 있지 않다)
```
테스트: `secondSaveReplacesPreviousState`

### 6.10 B10 · 부모 디렉터리 생성

```
Given  존재하지 않는 하위 폴더 경로 tempDir/nested/deep/infras.state.json 로 store 를 만든다
When   store.save(자원 하나)
Then   예외 없이 끝나고, 그 경로에 파일이 생기며, load() 가 그 자원을 돌려준다
```
테스트: `saveCreatesMissingParentDirectories`

### 6.11 B11 · 저장된 파일이 약속한 스키마다

```
Given  kind=TestKind.EC2, physicalId="i-0abc123" 인 자원 하나
When   save 후 파일을 JsonParser.parseString(Files.readString(stateFile)).getAsJsonObject()
Then   version==1,
       resources 가 배열이고 크기 1,
       항목의 kindType == TestKind.class.getName(),
       항목의 kindValue == "EC2",
       항목에 requiredFields 키가 없다
```
테스트: `savedFileMatchesTheDocumentedSchema`

> 문자열 `contains` 로 검증하지 않는다 — pretty printing 의 공백에 테스트가 묶이면 포맷을
> 바꾸는 순간 깨진다. `JsonParser` 로 파싱해 구조를 본다.

### 6.12 B12 · 깨진 JSON

| # | 테스트 | Given (파일 내용) | Then |
|---|---|---|---|
| 1 | `loadThrowsOnMalformedJson` | `{"version": 1, "resources": [` | `StateStoreException`, 메시지에 `stateFile` 경로 포함, `cause` 가 null 이 아니다 |
| 2 | `loadThrowsOnEmptyFile` | `""` | `StateStoreException` |
| 3 | `loadThrowsWhenRootIsNotAnObject` | `[]` | `StateStoreException` |

공통 단언 형태:
```java
assertThatThrownBy(() -> store.load())
        .isInstanceOf(StateStoreException.class)
        .hasMessageContaining(stateFile.toString());
```

> **이 셋이 이 feature 에서 가장 중요한 실패 테스트다.** 여기서 빈 값을 돌려주면 프레임워크가
> 이미 떠 있는 자원을 전부 다시 만든다(plan §4.3).

### 6.13 B13 · 스키마 버전

| # | 테스트 | Given | Then |
|---|---|---|---|
| 1 | `loadThrowsOnUnknownSchemaVersion` | `{"version":2,"resources":[]}` | `StateStoreException`, 메시지에 `2` 와 경로 |
| 2 | `loadThrowsWhenVersionIsMissing` | `{"resources":[]}` | `StateStoreException` (record 기본값 `0` → 검사에 걸린다) |

### 6.14 B14 · kindType 을 enum 으로 로드할 수 없다

| # | 테스트 | Given (`kindType` 값) | Then |
|---|---|---|---|
| 1 | `loadThrowsWhenKindClassIsNotFound` | `"com.example.NoSuchKind"` | `StateStoreException`, 메시지에 그 이름 |
| 2 | `loadThrowsWhenKindTypeIsNotAnEnum` | `"java.lang.String"` | `StateStoreException` |
| 3 | `loadThrowsWhenKindTypeIsAnEnumButNotAKind` | `"java.lang.Thread$State"` | `StateStoreException` |

`writeStateFile(stateJson(1, entryJson(kindType, "EC2")))` 로 파일을 심는다.
#3 이 있는 이유: `isEnum()` 만 보고 `Kind.class.isAssignableFrom` 을 빠뜨리면 캐스팅 시점에
`ClassCastException` 이 새어 나간다.

### 6.15 B15 · kindValue 로 상수를 특정할 수 없다

| # | 테스트 | Given | Then |
|---|---|---|---|
| 1 | `loadThrowsWhenNoConstantMatchesKindValue` | `kindType=TestKind`, `kindValue="RDS"` | `StateStoreException`, 메시지에 `RDS` |
| 2 | `loadThrowsWhenSeveralConstantsShareKindValue` | `kindType=DuplicateValueKind`, `kindValue="SAME"` | `StateStoreException` |

### 6.16 B16 · 항목의 필수 필드

| # | 테스트 | Given | Then |
|---|---|---|---|
| 1 | `loadThrowsWhenResourcesArrayIsMissing` | `{"version":1}` | `StateStoreException`, 메시지에 `resources` |
| 2 | `loadThrowsWhenLogicalIdIsMissing` | 항목에서 `logicalId` 키를 뺀 파일 | `StateStoreException`, 메시지에 `logicalId` |
| 3 | `loadThrowsWhenConfigValueIsNull` | `"config":{"port":null}` | `StateStoreException`, 메시지에 `port` |

> #3 이 없으면 `Map.copyOf` 가 **경로도 키 이름도 없는 NPE** 를 던진다. 사용자가 손으로 고친
> 상태 파일에서 실제로 나올 수 있는 모양이다.

### 6.17 B17 · 읽기 IO 실패

```
Given  상태 파일 경로가 파일이 아니라 디렉터리다 (new CurrentStateStore(tempDir))
When   load()
Then   StateStoreException 이 나고 (NoSuchFileException 이 아니므로 삼키지 않는다)
       메시지에 경로가 들어가며 cause 가 IOException 이다
```
테스트: `loadThrowsWhenStateFileCannotBeRead`

> 디렉터리를 읽으려 하면 macOS·Linux 모두 `IOException` 이 난다. CI 는 `ubuntu-latest` 다.
> 만약 어떤 환경에서 이 전제가 깨지면 이 테스트만 조정하고 **계약은 유지한다** —
> "IO 실패는 삼키지 않는다"가 본질이다.

### 6.18 B18 · enum 이 아닌 Kind 는 저장할 수 없다

```
Given  kind 가 NotEnumKind 인스턴스인 자원
When   store.save(resources)
Then   StateStoreException 이 나고, 메시지에 NotEnumKind 의 클래스 이름과 logicalId 와 경로가 있다
```
테스트: `saveThrowsWhenKindIsNotAnEnum`
추가 단언: **상태 파일이 만들어지지 않는다** (`Files.exists(stateFile)` 가 `false`) —
DTO 변환이 파일을 건드리기 전에 끝나므로.

---

## 7. 이번 범위 밖 / 알려진 한계 (테스트하지 않는 것)

1. **원자적 쓰기 자체의 검증.** "쓰는 도중 프로세스를 죽인다"는 단위 테스트로 재현할 수 없다.
   구현이 `Files.move(..., ATOMIC_MOVE)` 를 쓰는 것으로 담보하고, 이 판단을 `summary.md` 에 남긴다(plan §7).
2. **쓰기 IO 실패 경로.** 권한을 뺏는 테스트는 root 로 도는 CI 에서 무력화되고 OS 마다 다르게 돈다.
   §4.5 의 `catch (IOException)` 은 읽기 쪽(B17)과 같은 변환 규칙을 따르는 것으로 갈음한다.
3. **`config` 의 중첩 값(객체·배열).** 스키마상 스칼라만 오기로 되어 있다(plan §4.6 스킴 ②-2).
   중첩 값이 와도 예외를 던지지 않고 Gson 이 준 그대로(`LinkedTreeMap`/`ArrayList`) 넣는다.
   막는 것은 Validator 의 몫이고, 여기서 막으면 상태 저장소가 스캐너의 규칙을 이중으로 강제하게 된다.
4. **`long` 왕복 시 타입 손실.** `long sizeGb = 100` 은 `Integer 100` 으로 돌아온다(plan §4.5).
   **Comparator 는 `config` 값을 타입이 아니라 값으로 비교해야 한다** — `summary.md` 에 반드시 남긴다.
5. **동시 접근(락), 원격 백엔드, 스키마 마이그레이션, `InfraStruct.run()` 배선** — plan §6 그대로.
6. **예외를 사용자용 메시지로 바꾸는 일** — 경계(`InfraStruct.run()`)의 몫이다(plan §4.4).

---

## 8. 완료 기준 (`/qa` 로 넘어가기 전 체크)

1. §5 의 행동 18개가 모두 `done` 이다.
2. `./gradlew build` 가 그린이다 (테스트 + Spotless + Checkstyle + SpotBugs).
3. `new CurrentStateStore()` (무인자) 를 쓰는 코드가 레포에 하나도 없다.
4. `framework/src/main` 에 새로 생긴 `catch` 는 전부 §4 의 표에 있는 것뿐이고,
   문제를 사라지게 하는 `catch` 는 `NoSuchFileException ignored` 하나뿐이다.
5. `.gitignore` 의 `infras.state.json` 주석이 풀려 있다.
6. `spi/Kind.java` Javadoc 에 "enum 으로 구현해야 한다"가 요구사항으로 적혀 있다.
7. 테스트가 레포 워킹 트리에 파일을 남기지 않는다 (`git status` 가 깨끗하다).
