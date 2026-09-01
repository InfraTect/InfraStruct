# spec: desired-state-creator-impl

> **이 문서만 읽고 개발할 수 있어야 한다.**
> 구현자는 `docs/CONVENTIONS.md`(규칙) · `docs/CONTRIBUTING.md`(작업 방법) · 이 문서 셋과
> **소스 트리**만 가진 상태로 시작한다. 대화 맥락은 없다. 그래서 여기에는 행동 목록뿐 아니라
> **확정된 계약**, **알고리즘**, **fixture 설계**까지 전부 적어 둔다.
>
> 소스는 읽어도 되고, 읽는 편이 낫다. 다만 **역할이 다르다.**
>
> | 궁금한 것 | 어디를 보나 |
> |---|---|
> | 지금 코드가 어떻게 생겼나 (현재 시그니처·필드) | **소스가 정답.** §2 는 어디를 열지 알려 주는 색인일 뿐이다 |
> | 무엇으로 바꿔야 하나 (목표 계약) | **이 문서가 정답.** §3~§5. 소스는 아직 그렇게 안 생겼다 |
> | 왜 그렇게 정했나 | `plan.md`. 궁금할 때만 열면 되고, 열지 않아도 구현은 막히지 않는다 |
>
> 소스와 이 문서가 어긋나 보이면 대개 **아직 안 고친 것**이다(예: `BehaviorHandler.handle` 이
> `void` 인 것). 그건 버그가 아니라 §7 의 첫 행동이다.

> ⚠️ **이 파일에서 `- ` 로 시작하는 줄은 §7 의 행동 목록뿐이다.** 하네스(`.claude/harness/lib.sh`
> 의 `resync_behaviors`)가 `^\s*-\s+` 인 줄을 **전부** 체크리스트로 등록하기 때문이다. 다른 목록은
> 번호나 `*` 를 쓴다. 이 문서를 고칠 때도 같은 규칙을 지킬 것.

---

## 1. 무엇을 만드는가 (30초 요약)

`DesiredStateCreator.create(ScannedResources)` 의 본문. 스캐너가 모아 온 **"아직 안 풀린 지시서"**
(매크로 어노테이션)를 전부 풀어, 더 이상 풀 것이 없는 상태로 바꾼다.

```
ScannedResources ──자원마다──▶ capturedAnnotations 를 순서대로 핸들러에 먹인다 (fold)
                                          │
                                          ▼
                              config/dependencies 가 반영된 상태
                                          │
                                          ▼
                    DesiredResourceState ──▶ DesiredResources
```

핵심 아이디어는 **어노테이션이 자기 처리기를 데리고 다닌다**는 것이다. `@AllowSSH` 가
SecurityGroup 을 *어떻게* 고치는지 프레임워크는 모른다(코어는 클라우드를 모른다). 대신 매크로
어노테이션 선언에 `@Behavior(handler = AllowSshHandler.class)` 가 붙어 있고, **스캐너가 그것을 읽어
`CapturedAnnotation(anno, handlerClass)` 쌍으로 이미 담아 뒀다.** 그래서 이 모듈이 할 일은 하나다 —
그 쌍을 풀어 핸들러를 부른다. **핸들러 레지스트리는 필요 없다.**

어노테이션 **인스턴스**를 넘기는 이유는 `@AllowPort(port = 22)` 같은 인자 때문이다. 타입만으로는
부족하고 값이 필요하므로 핸들러가 `anno.port()` 를 읽는다.

---

## 2. 관련 타입 색인 (소스로 바로 갈 수 있게)

전부 `framework/src/main/java/com/infrastruct/<패키지>/<타입>.java` 다. 아래 요약은 **읽는 순서를
줄이려는 색인**이고, 세부는 소스와 그 Javadoc 이 정답이다. §3 에서 바뀌는 둘만 바뀐다.

먼저 열어 볼 것 셋: `internal/DesiredStateCreator`(채울 자리) ·
`internal/CapturedAnnotation`(입력의 핵심) · `internal/ResourceScanException`(전용 예외의 모범 사례).

| 타입 | 패키지 | 지금 모습 |
|---|---|---|
| `Kind` | `spi` | `interface Kind { String value(); }` |
| `ResourceState` | `spi` | `abstract`. 필드 `kind, logicalId, config(Map<String,Object>), dependencies(List<String>), requiredFields(Set<String>)` + 동명 getter. **불변** — 생성자에서 `Map.copyOf`/`List.copyOf`/`Set.copyOf` 한다 |
| `ScannedResourceState` | `spi` | `final extends ResourceState`. 생성자 6인자 `(kind, logicalId, config, dependencies, requiredFields, capturedAnnotations)` + `List<CapturedAnnotation> capturedAnnotations()` |
| `DesiredResourceState` | `spi` | `final extends ResourceState`. 생성자 5인자. 필드 추가 없음 |
| `ScannedResources` | `spi` | `record ScannedResources(List<ScannedResourceState> resources)` |
| `DesiredResources` | `spi` | `record DesiredResources(List<DesiredResourceState> resources)` |
| `CapturedAnnotation` | `internal` | `record CapturedAnnotation(Annotation anno, Class<? extends BehaviorHandler<?>> handlerClass)` |
| `Behavior` | `spi` | `@interface Behavior { Class<? extends BehaviorHandler<?>> handler(); }` — RUNTIME / ANNOTATION_TYPE |
| `BehaviorHandler<T extends Annotation>` | `spi` | **`void handle(T annotation, ScannedResourceState state)`** ← §3 에서 바뀐다 |
| `DesiredStateCreator` | `internal` | `final`. `public DesiredResources create(ScannedResources scanned)` — 본문이 `return new DesiredResources(List.of());` 인 **스텁** |
| `ResourceScanException` | `internal` | 전용 예외의 **모범 사례**. 이번에 만들 `DesiredStateException` 은 이것과 같은 모양이면 된다 |

`ResourceScanner` 는 아직 스텁이지만 **이번 작업은 막히지 않는다.** 테스트에서
`ScannedResources` 를 손으로 조립하기 때문이다.

기존 테스트 중 `BehaviorHandler` 를 구현하는 곳이 넷 있다(§8-1 에서 전부 손댄다):
`spi/BehaviorHandlerTest`, `spi/BehaviorTest`, `spi/ScannedResourceStateTest`,
`internal/CapturedAnnotationTest`.

---

## 3. 확정된 계약 (이번에 바뀌는 공개 시그니처)

### 3-1. `spi/BehaviorHandler` — 반환형을 상태로 바꾼다

```java
public interface BehaviorHandler<T extends Annotation> {

    ScannedResourceState handle(T annotation, ScannedResourceState state);
}
```

**왜 `void` 가 아닌가**: 상태는 불변인데 핸들러는 상태를 고쳐야 한다. `void` + 불변이면 핸들러가
할 수 있는 일이 없다. 반환값을 다음 핸들러의 입력으로 이어 붙이는(fold) 방식이 파이프라인 전체의
불변 규율(`CONVENTIONS` §9, `ResourceState` Javadoc)을 그대로 지킨다.

이 파일을 고치는 것은 정당하다 — 앞선 뼈대 feature 가 *"DesiredStateCreator 를 맡는 사람이 정한다"*
고 명시적으로 넘긴 숙제다. `spi` 는 프로바이더가 쥐는 표면이므로 Javadoc 도 함께 갱신한다
(반환값이 무엇인지, `null` 을 돌려주면 안 된다는 것, 식별자를 바꾸면 안 된다는 것).

### 3-2. `spi/ScannedResourceState` — 복사 헬퍼를 연다

```java
public ScannedResourceState withConfigEntry(String key, Object value);
```

`config` 에 항목 하나가 더해지거나 덮어써진 **새 인스턴스**를 돌려준다. 원본은 바뀌지 않는다
(`String.replace` 가 원본을 안 고치고 새 문자열을 주는 것과 같은 관용구).

**왜 여는가**: 이게 없으면 프로바이더 작성자가 핸들러마다 아래를 손으로 쓴다.

```java
Map<String, Object> config = new HashMap<>(state.config());
config.put("port", anno.port());
return new ScannedResourceState(state.kind(), state.logicalId(), config,
        state.dependencies(), state.requiredFields(), state.capturedAnnotations());
```

핸들러는 `capturedAnnotations` 에 관심이 없는데도 손으로 다시 넘겨야 하고, `dependencies` 를
빠뜨리면 그 자원의 의존 관계가 **조용히** 사라진다. `spi` 는 프로바이더가 가장 자주 쓰는
표면이라 그 반복이 그대로 버그 표면이 된다.

`withDependency` 같은 다른 헬퍼는 **지금 열지 않는다.** 매크로 대부분은 설정값을 넣는 일이고,
의존 관계를 더하는 핸들러가 실제로 나타나기 전에는 어떤 모양이 편할지 고를 근거가 없다.

### 3-3. `internal/DesiredStateException` — 신설

`CONVENTIONS` §8.3 그대로. `internal`, unchecked, 생성자 둘, `serialVersionUID`, 모듈당 하나.
`ResourceScanException` 을 그대로 본뜨면 된다.

```java
public class DesiredStateException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DesiredStateException(String message) { super(message); }

    public DesiredStateException(String message, Throwable cause) { super(message, cause); }
}
```

### 3-4. `internal/DesiredStateCreator` — 시그니처는 그대로, 본문만 채운다

```java
public DesiredResources create(ScannedResources scanned)
```

생성자는 계속 **인자 없는 기본 생성자**다. 핸들러 캐시는 필드가 아니라 `create()` **호출 지역**에
둔다 — 이 모듈을 상태 없는 모듈로 유지해 스레드 안전성 논의를 없애기 위해서다. 파이프라인에서
`create()` 는 실행당 한 번이라 캐시를 필드로 올려 얻을 것도 없다.

---

## 4. 변환 알고리즘

`create(ScannedResources scanned)`:

1. `scanned` 가 `null` 이면 `Objects.requireNonNull` 로 거부한다(`NullPointerException`).
2. `create()` 지역에 핸들러 캐시 `Map<Class<? extends BehaviorHandler<?>>, BehaviorHandler<?>>`
   를 하나 만든다.
3. `scanned.resources()` 를 **순서 그대로** 돈다. 자원 개수는 변하지 않는다 — 이 단계는 자원을
   더하거나 빼지 않고 **1:1 로 옮긴다.**
4. 자원마다 누적 상태 `acc` 를 그 자원으로 시작하고, **원본 자원의** `capturedAnnotations()` 를
   순서대로 돌며 `acc = handler.handle(anno, acc)` 로 이어 붙인다.
   * ⚠️ 순회 대상은 `acc.capturedAnnotations()` 가 **아니라 원본 목록**이다. 핸들러가 돌려준
     상태에 어노테이션이 더 붙어 있어도 처리하지 않는다. 그러지 않으면 핸들러가 자기 자신을 다시
     부르게 만들 수 있고 종료를 보장할 수 없다(어노테이션이 어노테이션을 낳는 기능은 범위 밖).
   * 순서는 스캐너가 정한 순서를 그대로 따른다 → **결정적**이다. 같은 `config` 키를 두
     어노테이션이 건드리면 **나중 것이 이긴다.**
5. 다 소비한 `acc` 에서 `kind`/`logicalId`/`config`/`dependencies`/`requiredFields` 를 꺼내
   `DesiredResourceState` 로 옮긴다. `capturedAnnotations` 는 **버린다** — 다 풀었다는 것이
   `DesiredResourceState` 라는 타입의 의미다.
6. 어노테이션이 없는 자원은 4번이 빈 루프라 그대로 5번으로 간다(= 순수 타입 변환).
7. `DesiredResources` 로 묶어 돌려준다. 빈 입력 → 빈 출력.

**`requiredFields` 를 검증하지 않는다.** 필수 필드가 실제로 채워졌는지 보는 것은 `Validator` 의
일이다(위반을 모아 보고해야 하므로 예외가 아니라 결과 객체 — `CONVENTIONS` §8.1). 이 모듈은
검증하지 않고 **변환만** 한다.

### 4-1. 핸들러 인스턴스 얻기

핸들러 계약은 **public 무인자 생성자**다. 핸들러는 **상태 없는 함수**로 본다. 그래서 `Class` 당
인스턴스 하나만 만들어 캐시에 두고 자원 여러 개가 공유한다.

```java
BehaviorHandler<?> handler =
        handlers.computeIfAbsent(captured.handlerClass(), key -> instantiate(key, /* 진단용 맥락 */));
```

`instantiate` 는 `key.getConstructor().newInstance()` 를 부르고
`ReflectiveOperationException` 을 잡아 `DesiredStateException` 으로 바꾼다(§5).
`getDeclaredConstructor()` 가 아니라 `getConstructor()` 인 이유는 계약이 **public** 무인자
생성자이기 때문이다 — 비공개 생성자는 `NoSuchMethodException` 으로 같은 메시지에 걸린다.

### 4-2. 제네릭 구멍 — 여기서만 unchecked 캐스트가 난다

`CapturedAnnotation` 은 `Annotation` 과 `Class<? extends BehaviorHandler<?>>` 를 따로 들고 있어,
"이 핸들러가 이 어노테이션을 받는다"를 컴파일러가 알 수 없다. `handle` 을 부르려면 결국
`BehaviorHandler<Annotation>` 으로의 비검사 캐스트가 필요하다.

캐스트는 **private 메서드 하나에 가둔다.** `@SuppressWarnings("unchecked")` 도 거기 한 곳에만.

```java
@SuppressWarnings("unchecked")
private static BehaviorHandler<Annotation> asAnnotationHandler(BehaviorHandler<?> handler) {
    return (BehaviorHandler<Annotation>) handler;
}
```

**부르기 전에** 핸들러가 선언한 타입 인자를 뽑아 `anno.annotationType()` 과 맞는지 본다. 안 맞으면
**부르지 않고** 예외를 던진다. 그러지 않으면 `ClassCastException` 이 핸들러 **안에서** 터져
사용자가 원인을 못 찾는다.

```java
/** 핸들러가 선언한 어노테이션 타입. 확정할 수 없으면 {@code null}. */
private static Class<?> declaredAnnotationType(Class<?> handlerClass) {
    for (Type type : handlerClass.getGenericInterfaces()) {
        if (type instanceof ParameterizedType parameterized
                && BehaviorHandler.class.equals(parameterized.getRawType())) {
            Type arg = parameterized.getActualTypeArguments()[0];
            if (arg instanceof Class<?> annotationType) {
                return annotationType;
            }
        }
    }
    return null;
}
```

`null` 이면(raw 구현, 타입 변수를 그대로 넘기는 구현, 상위 클래스가 대신 구현한 경우) 검사를
건너뛰고 호출한다. **상위 클래스까지 거슬러 올라가는 탐색은 이번 범위가 아니다** — 직접 구현한
인터페이스만 본다.

---

## 5. 실패 처리와 진단 메시지

**첫 실패에서 멈춘다**(결과 객체가 아니라 예외). 여기서 실패하면 뒤 단계로 넘길 값 자체를 만들 수
없기 때문이다(`CONVENTIONS` §8.1).

메시지에는 **사용자가 고쳐야 할 대상** 셋을 담는다 — 자원의 `logicalId`, 어노테이션 타입,
핸들러 FQCN. 이 셋이 없으면 "어느 자원의 어느 어노테이션이 문제인가"를 알 수 없다. 공통 접두를
private 헬퍼로 뽑아 모든 메시지가 같은 모양이 되게 한다.

```java
private static String context(String logicalId, CapturedAnnotation captured) {
    return "자원 '" + logicalId + "' 의 어노테이션 @"
            + captured.anno().annotationType().getName()
            + " (핸들러 " + captured.handlerClass().getName() + ")";
}
```

`logicalId` 는 항상 **원본 자원의 것**을 쓴다(핸들러가 바꿔 돌려준 값을 쓰면 진단이 거짓말을 한다).

| 상황 | 왜 예외인가 | `cause` |
|---|---|---|
| 핸들러에 public 무인자 생성자가 없다 / 추상 클래스다 / 생성자가 던졌다 | 프로바이더의 선언 실수, 진행 불가 | `ReflectiveOperationException` |
| 어노테이션 타입과 핸들러의 타입 인자가 다르다 | `@Behavior` 를 잘못 연결한 것 | 없음 |
| 핸들러가 `null` 을 돌려줬다 | 계약 위반 | 없음 |
| 핸들러가 `logicalId` 또는 `kind` 를 바꿔 돌려줬다 | `logicalId` 는 Comparator 의 매칭 키다. 여기서 바뀌면 뒤 단계에서 "삭제 + 생성"으로 보인다 | 없음 |
| 핸들러가 예외를 던졌다 | 맥락(어느 자원/어느 어노테이션)을 붙여 다시 던진다 | 핸들러가 던진 예외 |

`dependencies` 와 `requiredFields` 변경은 **허용**한다. 어노테이션이 의존 자원을 덧붙이는 것은
정상적인 매크로의 일이다. 잠그는 것은 **식별자(`kind`, `logicalId`)** 뿐이다.

`kind` 비교는 `Objects.equals`, `logicalId` 비교는 `String.equals` 를 쓴다.

> ⚠️ Checkstyle 함정 둘. (1) `IllegalThrows` 가 `RuntimeException`·`Throwable` 을 **직접** throw
> 하는 것을 막는다 — 전용 예외를 던지면 걸리지 않는다. (2) `JavadocMethod(validateThrows=true)` 는
> **Javadoc 이 달린 메서드**에서 던지는 예외에 `@throws` 태그를 요구한다. private 헬퍼에 Javadoc 을
> 달았다면 거기에도 `@throws` 를 적거나, 아예 Javadoc 없이 한 줄 `//` 주석만 남긴다.

---

## 6. 이번 범위

**한다**

1. `internal/DesiredStateCreator.create()` 본문 구현.
2. `internal/DesiredStateException` 신설.
3. `spi/BehaviorHandler.handle` 시그니처 변경 + Javadoc 갱신.
4. `spi/ScannedResourceState.withConfigEntry(String, Object)` + Javadoc.
5. 테스트 fixture(§9) 와 테스트. fixture 는 `src/test` 아래에만 둔다 — 코어에 프로바이더 코드가
   들어가지 않는다는 규율을 지킨다.
6. 시그니처 변경으로 깨지는 기존 테스트 넷 수정(§8-1).

**안 한다 (그리고 왜)**

1. 실제 `@AllowSSH` / `AllowSshHandler` → 프로바이더 레포의 몫. 여기선 fixture 로만 흉내낸다.
2. `Validator` 연결, `InfraStruct.run()` 배선 → 배선 feature 의 몫.
3. `ResourceScanner` 본문 → 다른 브랜치.
4. 어노테이션이 어노테이션을 낳는 재귀 처리(§4-4).
5. 핸들러 실행 순서를 사용자가 지정하는 기능(우선순위). 필요해지면 `@Behavior` 에 속성을 더한다.
6. `withDependency` 등 다른 복사 헬퍼(§3-2).

---

## 7. 행동 목록 (red 사이클 순서)

아래 `- ` 불릿이 첫 `/red` 때 `state.json` 의 `behaviors[]` 로 등록되고, **위에서부터 순서대로**
red→green→refactor 를 돈다. 각 행동의 테스트를 어떻게 쓰고 어떻게 RED 를 확보하는지는 §8 에
같은 번호로 적어 뒀다.

- BehaviorHandler.handle 은 반영된 ScannedResourceState 를 돌려준다
- withConfigEntry 는 그 항목만 더하거나 덮어쓴 사본을 돌려주고 원본과 나머지 필드는 그대로다
- create 는 null 입력을 NullPointerException 으로 거부한다
- 어노테이션이 없는 자원들은 순서와 필드를 유지한 채 DesiredResourceState 로 1대1 변환된다
- 어노테이션 인스턴스가 그대로 핸들러에 전달되어 인자 값이 config 에 반영된다
- 같은 config 키를 건드리는 어노테이션 둘은 스캔 순서대로 적용되어 나중 것이 이긴다
- 같은 핸들러 클래스는 create 한 번 안에서 인스턴스 하나만 만들어 자원들이 공유한다
- 무인자 생성자가 없는 핸들러는 DesiredStateException 이고 메시지에 logicalId 와 핸들러 FQCN 이 있다
- 어노테이션 타입과 핸들러 타입 인자가 다르면 handle 을 부르지 않고 DesiredStateException 이다
- 핸들러가 null 을 돌려주면 DesiredStateException 이다
- 핸들러가 kind 나 logicalId 를 바꿔 돌려주면 DesiredStateException 이고 dependencies 추가는 허용된다
- 핸들러가 던진 예외는 cause 로 붙어 DesiredStateException 으로 다시 나온다

---

## 8. 사이클별 안내

### 8-0. RED 게이트를 넘기는 법 (먼저 읽을 것)

`transition.sh green` 은 `./gradlew test` 가 **실제로 실패**해야만 통과시킨다. 그래서 사이클마다
**진짜 실패 하나**를 확보해야 한다. 두 가지를 지킨다.

1. **green 은 그 사이클의 테스트가 요구하는 최소한만 구현한다.** 뒤 행동까지 미리 만들면 그
   행동에서 RED 를 만들 수 없어 사이클이 막힌다.
2. 그럼에도 **이미 green 인 행동이 나올 수 있다**(앞 사이클의 자연스러운 구현이 덮어버리는 경우).
   그때는 그 행동의 테스트를 **다음 실패 테스트와 같은 사이클에 묶어** 쓰고, 두 행동을 함께
   마감한다. 하네스가 강제하는 것은 "행동 라벨과 테스트가 1:1인가"가 아니라 **"선언한 행동 수만큼
   진짜 red→green 을 돌렸나"**이다. 순서를 바꿨거나 묶었으면 `summary.md` 에 "스펙과 달라진 점"
   으로 남긴다. 아래에서 그럴 가능성이 있는 행동에는 ⚠️ 를 붙여 뒀다.

컴파일 실패도 정상적인 RED 다. 없는 메서드를 부르는 테스트를 먼저 써도 된다.

### 8-1. `BehaviorHandler.handle` 반환형 (행동 1)

이 사이클이 **제일 먼저**여야 한다. 시그니처가 바뀌면 기존 테스트 넷이 컴파일되지 않는데,
green 단계에서는 `src/test` 편집이 막히기 때문이다. 즉 **red 단계에서 테스트 넷을 새 시그니처로
미리 고쳐 둬야** green 단계가 통과한다.

RED (테스트만 편집):

1. `spi/BehaviorHandlerTest.RecordingHandler` 를 새 시그니처로 고친다. 받은 인자를 기록하고
   `state.withConfigEntry(...)` 가 아니라 그냥 **`state` 를 그대로 돌려주게** 둔다
   (`withConfigEntry` 는 아직 없다). 테스트는 반환값이 넘긴 상태와 `isSameAs` 인지 본다.
2. 나머지 셋(`spi/BehaviorTest`, `spi/ScannedResourceStateTest`,
   `internal/CapturedAnnotationTest`)의 `FixtureHandler` 는 본문이 빈 `void` 메서드다.
   `return state;` 로 바꾸고 반환형만 고친다. 이 셋은 단언을 추가하지 않는다 — 컴파일만 맞춘다.

→ 컴파일 실패(main 이 아직 `void`)로 RED.

GREEN: `spi/BehaviorHandler` 의 반환형을 `ScannedResourceState` 로 바꾸고 Javadoc 에 `@return` 을
추가한다. **`withConfigEntry` 는 아직 만들지 않는다** (다음 사이클의 RED 다).

### 8-2. `withConfigEntry` (행동 2)

RED: `spi/ScannedResourceStateTest` 에 테스트 하나. 없는 메서드라 컴파일 실패.

```java
ScannedResourceState origin = /* config={"a",1}, dependencies=["dep"], requiredFields={"a"},
                                 capturedAnnotations=[captured()] 인 상태 */;

ScannedResourceState added = origin.withConfigEntry("b", 2);
ScannedResourceState overwritten = origin.withConfigEntry("a", 9);
```

단언 넷: `added.config()` 가 `a=1, b=2` 를 담고 · `overwritten.config()` 의 `a` 가 `9` 이고 ·
`origin.config()` 는 여전히 `{a=1}` 이고 · `added` 의 `dependencies`/`requiredFields`/
`capturedAnnotations` 가 원본과 같다.

> 마지막 단언이 중요하다. 이게 없으면 나머지 필드를 빠뜨린 구현도 통과한다 — 그리고 그 실수는
> 실제로 `dependencies` 를 통째로 날린다(§3-2).

GREEN: `new HashMap<>(config())` 에 `put` 한 뒤 6인자 생성자로 새 인스턴스를 만든다. 생성자가
`Map.copyOf` 로 다시 불변화하므로 여기서 따로 감쌀 필요는 없다.

### 8-3. `create(null)` (행동 3)

RED: `assertThatThrownBy(() -> new DesiredStateCreator().create(null))
.isInstanceOf(NullPointerException.class)`. 지금 스텁은 `scanned` 를 아예 안 만져서 그냥 빈
결과를 돌려주므로 실패한다.

GREEN: `Objects.requireNonNull(scanned, "scanned")` 한 줄. 그 아래는 아직 스텁 그대로 둔다.

### 8-4. 어노테이션 없는 자원의 1:1 변환 (행동 4)

이 사이클에서 **기존 스텁 테스트
`DesiredStateCreatorTest.createReturnsEmptyDesiredResourcesForNowEvenWithScannedInput` 를 지운다.**
스텁 전제("항상 빈 결과")를 못 박은 테스트라 여기서 수명이 끝난다.

RED: 어노테이션이 없는 자원 **둘**(`vpc.myVpc`, `sg.mySg`)로 `ScannedResources` 를 만들어
`create()` 를 부른다. 단언: `resources()` 크기가 2 이고 · `logicalId` 순서가 넘긴 순서와 같고 ·
첫 자원의 `kind`/`config`/`dependencies`/`requiredFields` 가 입력과 같고 · 원소 타입이
`DesiredResourceState` 다.

> 자원을 **둘** 쓰는 이유는 순서 유지와 1:1 을 한 테스트로 같이 못 박기 위해서다. 하나만 쓰면
> "첫 자원만 변환하는" 구현도 통과한다.

GREEN: §4 의 3·5·7번(자원 순회 + 필드 이관 + 묶기). **핸들러 호출은 아직 넣지 않는다.**

### 8-5. 어노테이션 인스턴스가 인자 값째 반영된다 (행동 5)

RED: `@AllowPort(port = 22)` 인스턴스와 `AllowPortHandler.class` 로 `CapturedAnnotation` 을 만들어
자원 하나에 붙인다. 단언: 결과 `config` 에 `port=22` 가 있다.

> 기본값(22)이 아니라 **다른 값**을 쓰는 편이 낫다. fixture 의 `@AllowPort` 는 `default 22` 이므로
> 홀더에 `@AllowPort(port = 8080)` 을 붙여 `port=8080` 을 단언하면, 어노테이션 **인스턴스**가
> 전달됐다는 것까지 반증 가능해진다(기본값을 하드코딩한 구현이 통과하지 못한다).

GREEN: §4 의 4번(fold) + §4-1(인스턴스화, 캐시 없이 매번 생성해도 이 테스트는 통과한다) +
§4-2 의 캐스트. 타입 인자 검사는 아직 넣지 않는다.

### 8-6. 순서와 "나중 것이 이긴다" (행동 6) ⚠️

RED: 같은 키 `port` 를 건드리는 `CapturedAnnotation` 둘을 순서대로 붙인다
(`@AllowPort(port = 22)` → `@AllowPort(port = 443)`). 단언: 결과 `config` 의 `port` 가 `443`.

⚠️ 앞 사이클의 fold 구현이 이미 이것을 만족할 수 있다. 그러면 §8-0 의 2번을 따라 다음 행동의
테스트와 같은 사이클에 묶는다. 그래도 **테스트 자체는 반드시 남긴다** — 이 순서 규칙은 문서화된
계약이고, 나중에 누가 `Map` 병합 순서를 뒤집으면 이 테스트만이 그것을 잡는다.

### 8-7. 핸들러 인스턴스 재사용 (행동 7)

RED: 자원 **둘**에 각각 `IdentityStampingHandler` 를 붙인다(§9). 이 핸들러는
`System.identityHashCode(this)` 를 `config["handlerId"]` 에 찍는다. 단언: 두 자원의 `handlerId`
가 **같다**. 캐시가 없으면 자원마다 새 인스턴스가 생겨 값이 달라진다.

> static 카운터를 쓰지 않는 이유: 테스트 간 전역 상태를 만들면 실행 순서에 따라 깨지고, SpotBugs 의
> 가변 static 규칙에도 걸린다. 같은 값이 두 번 찍혔다는 것만으로 "같은 인스턴스"는 충분히 증명된다.

GREEN: §4 의 2번 캐시(`computeIfAbsent`)를 넣는다.

### 8-8 ~ 8-12. 실패 다섯 (행동 8~12)

전부 `DesiredStateException` 이 아직 없으므로 **컴파일 실패로 확실한 RED** 가 난다. 첫 실패
사이클(행동 8)에서 예외 클래스를 만든다.

공통 단언 골격:

```java
assertThatThrownBy(() -> new DesiredStateCreator().create(scanned))
        .isInstanceOf(DesiredStateException.class)
        .hasMessageContaining("vpc.myVpc")
        .hasMessageContaining(XxxHandler.class.getName());
```

| 행동 | fixture 배치 | 추가 단언 |
|---|---|---|
| 8 | `NoDefaultCtorHandler` + `@Encrypted` 인스턴스 | `hasCauseInstanceOf(ReflectiveOperationException.class)` |
| 9 | `EncryptHandler`(= `BehaviorHandler<Encrypted>`) + **`@AllowPort` 인스턴스** | 메시지에 두 타입 이름이 다 들어간다. 핸들러가 실제로 안 불렸음은 "`ClassCastException` 이 아니라 `DesiredStateException` 이다"로 갈음한다 |
| 10 | `NullReturningHandler` + `@Encrypted` | `hasNoCause()` |
| 11 | `LogicalIdChangingHandler`, `KindChangingHandler` 각각 + `@Encrypted` | 같은 테스트에서 `DependencyAddingHandler` 는 **예외 없이** 통과하고 결과 `dependencies` 에 새 값이 들어 있음을 단언한다 |
| 12 | `ThrowingHandler` + `@Encrypted` | `hasCauseInstanceOf(IllegalStateException.class)` 와 원인 메시지 보존 |

행동 9 의 배치가 요점이다 — 핸들러는 `Encrypted` 를 받는데 `AllowPort` 인스턴스를 짝지어
`CapturedAnnotation` 을 만든다. `CapturedAnnotation` 이 둘을 따로 들고 있어 컴파일은 통과하고,
런타임에 §4-2 의 검사가 잡아야 한다.

행동 11 을 한 사이클로 묶는 이유는 이 셋이 **하나의 결정**이기 때문이다 — "잠그는 것은 식별자뿐,
`dependencies` 는 열어 둔다". 따로 떼면 "무엇을 허용하는가"가 어느 테스트에도 안 남는다.

---

## 9. Test fixture 설계

`framework/src/test/java/com/infrastruct/fixture/desired/` 아래에 둔다. `fixture/scan/` 과 같은
자리다. **파일당 top-level 타입 하나**(Checkstyle `OneTopLevelClass`)이므로 아래는 파일 하나씩이다.

핸들러는 리플렉션으로 다른 패키지에서 인스턴스화되므로 **`public` 클래스에 `public` 무인자
생성자**여야 한다(그것을 일부러 어기는 `NoDefaultCtorHandler` 만 예외). 어노테이션 **인스턴스**를
얻기 위한 홀더 클래스는 public 일 필요가 없으므로 테스트 클래스 안의 중첩 클래스로 둔다.

### 9-1. 공통 토대

| 파일 | 내용 |
|---|---|
| `DesiredKind.java` | `public enum DesiredKind implements Kind { VPC, SECURITY_GROUP; @Override public String value() { return name(); } }`. `Kind` 구현이 enum 이어야 하는 이유는 `CONVENTIONS` §9.1 |

### 9-2. 매크로 어노테이션 둘

둘 다 `@Retention(RUNTIME)` · `@Target(TYPE)` 이고 `@Behavior` 를 붙인다. `DesiredStateCreator` 는
`@Behavior` 를 읽지 않지만(핸들러는 `CapturedAnnotation` 이 들고 온다), 실제 프로바이더가 쓰는
모습 그대로 둬서 fixture 가 계약을 문서화하게 한다.

| 파일 | 선언 |
|---|---|
| `AllowPort.java` | `@Behavior(handler = AllowPortHandler.class) public @interface AllowPort { int port() default 22; }` |
| `Encrypted.java` | `@Behavior(handler = EncryptHandler.class) public @interface Encrypted {}` — 멤버 없음 |

`AllowPort` 에 멤버를 둔 이유는 **어노테이션 인스턴스가 실제로 전달되는지**를 반증 가능하게 만들기
위해서다. 멤버가 없으면 타입만 보고 값을 하드코딩한 구현도 통과한다.

### 9-3. 핸들러

전부 `public final class ... implements BehaviorHandler<...>` 다.

| 파일 | 타입 인자 | `handle` 이 하는 일 |
|---|---|---|
| `AllowPortHandler` | `AllowPort` | `state.withConfigEntry("port", annotation.port())` |
| `EncryptHandler` | `Encrypted` | `state.withConfigEntry("encrypted", Boolean.TRUE)` |
| `IdentityStampingHandler` | `Encrypted` | `state.withConfigEntry("handlerId", System.identityHashCode(this))` — 인스턴스 재사용 검증용(§8-7) |
| `NullReturningHandler` | `Encrypted` | `return null;` — 계약 위반 |
| `ThrowingHandler` | `Encrypted` | `throw new IllegalStateException("handler boom");` |
| `LogicalIdChangingHandler` | `Encrypted` | 6인자 생성자로 `logicalId` 만 `state.logicalId() + "-renamed"` 인 새 상태를 만들어 반환 |
| `KindChangingHandler` | `Encrypted` | 같은 방식으로 `kind` 만 `DesiredKind.SECURITY_GROUP` 으로 바꿔 반환 |
| `DependencyAddingHandler` | `Encrypted` | `dependencies` 에 `"vpc.added"` 를 더한 새 상태를 반환(나머지 필드는 그대로) |
| `NoDefaultCtorHandler` | `Encrypted` | 생성자가 `NoDefaultCtorHandler(String label)` 하나뿐 — 무인자 생성자가 없다. `handle` 은 `state.withConfigEntry("label", label)` |

**"타입 불일치" 전용 핸들러는 만들지 않는다.** `EncryptHandler` 를 `@AllowPort` 인스턴스와 짝지어
`CapturedAnnotation` 을 만들면 불일치가 그 자리에서 만들어진다(§8-8 표의 행동 9).

`LogicalIdChangingHandler` 류가 6인자 생성자를 직접 쓰는 것은 의도한 것이다 — `withConfigEntry` 로는
식별자를 못 바꾸므로, 계약을 어기려면 그 방법밖에 없다.

### 9-4. 어노테이션 인스턴스 얻기 (테스트 클래스 안)

```java
@Encrypted
private static final class EncryptedHolder {}

@AllowPort(port = 8080)
private static final class Port8080Holder {}

@AllowPort(port = 443)
private static final class Port443Holder {}

private static Encrypted encrypted() {
    return EncryptedHolder.class.getAnnotation(Encrypted.class);
}
```

`CapturedAnnotation` 조립 헬퍼도 테스트 클래스 안에 둔다.

```java
private static CapturedAnnotation captured(
        Annotation anno, Class<? extends BehaviorHandler<?>> handlerClass) {
    return new CapturedAnnotation(anno, handlerClass);
}

private static ScannedResourceState scannedResource(
        String logicalId, List<CapturedAnnotation> annotations) {
    return new ScannedResourceState(
            DesiredKind.VPC, logicalId, Map.of(), List.of(), Set.of(), annotations);
}
```

테스트는 `internal/DesiredStateCreatorTest` 하나에 모은다(fixture 는 별도 패키지). 예외 계열이
길어지면 `DesiredStateCreatorFailureTest` 로 갈라도 된다 — 그때는 위 헬퍼를 양쪽에서 쓸 수 있게
fixture 패키지의 `public` 헬퍼로 올린다.

---

## 10. 이번 범위에서 **검증하지 않는** 것 (그리고 왜)

행동 목록에 넣지 않은 것들이다. 빠뜨린 게 아니라 **RED 를 만들 수 없어서** 뺐다. `/qa` 단계에서
커버리지를 위해 회귀 테스트로 덧붙이는 것은 자유다(그때는 행동으로 등록되지 않는다).

1. **빈 입력 → 빈 출력.** 현재 스텁이 이미 만족한다. 어느 사이클에서도 RED 가 나오지 않는다.
2. **결과에 소비할 어노테이션이 남아 있지 않다.** `DesiredResourceState` 에 그 필드가 없다 —
   **타입으로 보장**되므로 테스트로 반증할 수 없다. 이것이 두 타입을 갈라 둔 이유 자체다.
3. **원본 `ScannedResources` 가 안 바뀐다.** 상태 클래스가 불변이라 구조적으로 보장된다. 깨뜨리는
   구현을 쓸 수 없으니 RED 도 만들 수 없다.
4. **타입 인자를 확정할 수 없는 핸들러(raw 구현)를 검사 없이 부른다.** `BehaviorHandler` 를 raw 로
   구현하는 fixture 를 만들면 컴파일 경고가 나고, 그것을 억제하는 코드가 검증 가치보다 유지비가
   크다. 방어 경로로만 남긴다.
5. **상위 클래스가 `BehaviorHandler` 를 대신 구현한 핸들러.** §4-2 가 명시적으로 범위 밖이다.
6. **`DesiredStateException` 자체의 생성자·메시지 보존.** 행동 8·12 가 이미 메시지와 `cause` 를
   단언하므로 별도 테스트는 중복이다.

---

## 11. 완료 조건

1. 행동 12 개가 전부 `done` (`/tdd-status` 로 확인).
2. `./gradlew spotlessApply check` 가 통과한다(포맷·Checkstyle·SpotBugs·테스트·JaCoCo).
   JDK 21 이 필요하다 — `CONTRIBUTING.md` §1.
3. 새로 열린 `spi` 표면 둘(`BehaviorHandler.handle`, `ScannedResourceState.withConfigEntry`)에
   한국어 Javadoc 이 있고, "무엇"이 아니라 **"왜"** 와 계약(`null` 금지, 식별자 불변, 원본 불변)을
   적었다. 프로바이더 작성자는 소스를 안 보고 IDE 호버로만 이걸 읽는다.
4. `DesiredStateCreator` 의 클래스 Javadoc 에서 "뼈대(스텁)" 문구를 걷어내고 실제 계약으로 바꿨다.
5. `docs/features/2026-08-20-desired-state-creator-impl/summary.md` 를 썼다. §8-0 때문에 사이클
   순서를 바꾸거나 묶었다면 "스펙과 달라진 점"으로 남긴다.
6. 분량 예상: main 250줄 안팎(예외 클래스 포함), test 350줄 안팎. **한 PR** 로 간다.
