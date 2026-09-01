# feature: desired-state-creator-impl

브랜치: `feat/DesiredStateCreator-impl-v2`

> 앞선 뼈대 feature(`2026-08-14-desired-state-creator`)가 "설계 전제가 아직 안 섰다"며 비워 둔
> 본문을 이번에 채운다. 그 plan §4 가 미룬 두 숙제(**`handle` 시그니처 확정**, **핸들러 조회**)를
> 여기서 결론낸다.

---

## 1. 목표 (무엇을)

`DesiredStateCreator.create(ScannedResources)` 의 **실제 변환 로직**.

```
ScannedResources ──(자원마다)──▶ capturedAnnotations 를 순서대로 핸들러에 먹임
                                          │
                                          ▼
                              config/dependencies 가 반영된 상태
                                          │
                                          ▼
                    DesiredResourceState ──▶ DesiredResources
```

이 모듈의 한 줄 정의: **"아직 안 풀린 지시서(매크로 어노테이션)를 전부 풀어, 더 이상 풀 것이 없는
상태로 바꾸는 단계"**. `ScannedResourceState` 와 `DesiredResourceState` 를 타입으로 갈라 둔 이유가
그대로 이 모듈의 계약이다 — 이 단계를 지나면 `capturedAnnotations` 가 사라져 있어야 한다.

---

## 2. 핵심 아이디어 — 어노테이션은 자기 처리기를 데리고 다닌다

`@AllowSSH` 가 SecurityGroup 을 **어떻게** 고치는지는 프레임워크가 모른다. 알아야 할 이유도 없다
(코어는 클라우드를 모른다 — `docs/plan.md` §0).

대신 매크로 어노테이션 선언에 메타 어노테이션 `@Behavior(handler = AllowSshHandler.class)` 가
붙어 있고, 스캐너가 그것을 읽어 `CapturedAnnotation(anno, handlerClass)` 쌍으로 이미 담아 뒀다.
그래서 `DesiredStateCreator` 가 하는 일은 딱 하나다 — **그 쌍을 풀어 핸들러를 부른다.**

```java
// 프로바이더가 쓰는 쪽 (별도 레포)
@Behavior(handler = AllowSshHandler.class)
public @interface AllowSSH {}

@Behavior(handler = AllowPortHandler.class)
public @interface AllowPort { int port(); }
```

**어노테이션 인스턴스를 통째로 넘기는 이유**가 `AllowPort(port = 22)` 다. 어떤 어노테이션이
붙었는지(타입)만으로는 부족하고 **인자 값**이 필요하므로, 핸들러는 `Annotation` 인스턴스를 받아
`anno.port()` 를 읽는다. 이 때문에 `CapturedAnnotation.anno` 가 `Class` 가 아니라 인스턴스다.

정리하면 어노테이션은 자신이 자원을 어떻게 고치는지 **간접적으로** 안다 — 직접 고치는 코드는
핸들러에 있고, 어노테이션은 그 핸들러를 가리키는 포인터를 메타 어노테이션으로 들고 있을 뿐이다.

---

## 3. 뼈대 feature 가 남긴 숙제 ②: 핸들러 조회 — 레지스트리는 필요 없다

앞선 plan §4-2 는 *"어떤 `CapturedAnnotation` 을 어느 핸들러로 보낼지 찾아 주는 레지스트리가
필요한데 주입 경로(ModuleRegistry)가 없다"* 를 이유로 구현을 미뤘다. 이 전제는 **틀렸다.**

`CapturedAnnotation` 이 이미 `handlerClass` 를 들고 있다. 조회할 것이 없다 — 스캐너가 `@Behavior`
를 읽는 시점에 이미 답을 알아서 함께 담아 놨기 때문이다. 필요한 것은 레지스트리가 아니라
**`Class` → 인스턴스 하나**뿐이고, 그건 리플렉션으로 지금 할 수 있다.

- 핸들러 계약: **public 인자 없는 생성자**를 가진다 (없으면 예외 — 메시지에 핸들러 FQCN).
- 핸들러는 **상태 없는 함수**로 본다. 그래서 `create()` 한 번 안에서 `Class` 당 인스턴스 하나만
  만들어 캐시(`Map<Class, BehaviorHandler<?>>`)에 두고 자원 여러 개가 공유한다.
- 캐시를 필드가 아니라 **`create()` 호출 지역**에 두는 이유: `DesiredStateCreator` 를 상태 없는
  모듈로 유지해 스레드 안전성 논의를 아예 없앤다. 파이프라인에서 `create()` 는 실행당 한 번이라
  캐시 재사용으로 얻을 것도 없다.

→ **ModuleRegistry 를 기다릴 이유가 없다. 이번 feature 는 다른 브랜치에 블록되지 않는다.**

---

## 4. 뼈대 feature 가 남긴 숙제 ①: `handle` 시그니처 — ✅ **확정: 안 A** (2026-08-20 검토)

현재 계약은 `void handle(T annotation, ScannedResourceState state)` 이고, `resource-state-classes`
summary 가 *"파라미터 타입만 좁혀서는 동작하지 않는다"* 며 열어 둔 자리다.

문제는 한 줄로 요약된다: **상태는 불변인데 핸들러는 상태를 고쳐야 한다.** `void` + 불변이면
핸들러가 할 수 있는 일이 없다. 셋 중 하나를 골라야 한다.

### 안 A — 반환형을 상태로 ✅ **채택**

```java
ScannedResourceState handle(T annotation, ScannedResourceState state);
```

`DesiredStateCreator` 는 반환값을 다음 핸들러의 입력으로 이어 붙인다(fold).

```java
ScannedResourceState acc = scanned;
for (CapturedAnnotation captured : scanned.capturedAnnotations()) {   // 원본 목록 기준
    acc = handlerFor(captured).handle(captured.anno(), acc);
}
```

- ✅ 파이프라인 전체의 불변 규율(`ResourceState` Javadoc, `CONVENTIONS` §9)을 그대로 지킨다.
- ✅ 핸들러가 순수 함수라 단위 테스트가 쉽다 — 넣은 것과 나온 것만 보면 된다.
- ❌ 핸들러 작성이 번거롭다. 그대로 두면 프로바이더 작성자가 매번 이걸 쓴다:
  ```java
  Map<String, Object> config = new HashMap<>(state.config());
  config.put("ingress.22", "0.0.0.0/0");
  return new ScannedResourceState(state.kind(), state.logicalId(), config,
          state.dependencies(), state.requiredFields(), state.capturedAnnotations());  // 6인자
  ```
  핸들러는 `capturedAnnotations` 에 관심이 없는데 그걸 손으로 다시 넘겨야 한다.
  → **완화책**: `ScannedResourceState` 에 복사 헬퍼를 하나 연다.
  ```java
  ScannedResourceState withConfigEntry(String key, Object value);   // 위 6줄 → 한 줄
  ```
  이건 `spi` 파일 추가 편집이라 §7 에 범위로 명시한다.
- ❌ 핸들러가 `null` 을 돌려주거나 `logicalId`/`kind` 를 바꿔 돌려줄 수 있다 → 엔진이 검사해야 한다(§6).

### 안 B — 가변 편집 객체를 넘긴다

```java
void handle(T annotation, ResourceDraft draft);   // draft.putConfig("port", 22)
```

- ✅ 핸들러 작성이 가장 편하다. `void` 라 다이어그램과도 맞고, `null` 반환/식별자 변조가 **구조적으로
  불가능**하다(draft 에 `logicalId` setter 를 안 만들면 된다).
- ❌ `spi` 에 새 타입이 하나 늘고, 이미 머지된 `handle` 의 **파라미터 타입까지** 바뀐다.
- ❌ "각 단계는 앞 단계 상태를 고치지 않는다"는 규율에 가변 그릇이 하나 끼어든다(수명이 이 단계
  안으로 한정되긴 한다).

### 안 C — 시그니처를 그대로 두고 상태를 가변으로

기각. `Comparator` 가 비교 중에 대상이 안 바뀐다는 전제, `CurrentStateStore` 왕복, 상태 하나를 여러
단계가 공유하는 구조가 전부 불변에 기대고 있다. 이 하나를 위해 그걸 흔들 수 없다.

> ✅ **확정 — 안 A.** (검토 코멘트: *"A 안이 좋은것 같아."*) 불변이 이 프로젝트에서 이미 여러
> 결정의 전제로 쓰이고 있어 되돌리는 비용이 크고, A 의 유일한 약점(번거로움)은 아래 §4-1 의 헬퍼로
> 대부분 사라진다. B 의 안전성 이점은 엔진 쪽 검사(§6)로 대체한다.
> 따라서 `spi/BehaviorHandler` 의 최종 계약은:
>
> ```java
> ScannedResourceState handle(T annotation, ScannedResourceState state);
> ```

### 4-1. `withConfigEntry` 가 뭔가 — "고친 사본을 돌려주는 메서드"

불변 객체는 **고칠 수 없으므로, 고치는 대신 고쳐진 새 객체를 만든다.** Java 표준 라이브러리가
이미 이 방식이다:

```java
String a = "hello";
String b = a.replace('h', 'j');   // a 는 그대로 "hello", b 가 새 문자열 "jello"
```

`a.replace(...)` 가 `a` 를 바꾸지 않고 **바뀐 사본을 돌려주듯**, `state.withConfigEntry(...)` 도
`state` 를 바꾸지 않고 **config 에 항목 하나가 더/덮어써진 새 `ScannedResourceState`** 를 돌려준다.
이름 앞의 `with-` 는 "이 값을 가진 사본을 달라"는 Java 관용구다 (`record` 나 불변 클래스에서 흔하다).

**이게 없으면 핸들러 작성자가 매번 써야 하는 코드:**

```java
public ScannedResourceState handle(AllowPort anno, ScannedResourceState state) {
    Map<String, Object> config = new HashMap<>(state.config());          // ① 불변이라 복사하고
    config.put("port", anno.port());                                     // ② 사본을 고친 뒤
    return new ScannedResourceState(                                     // ③ 전 필드를 다시 넘겨 재조립
            state.kind(), state.logicalId(), config,
            state.dependencies(), state.requiredFields(), state.capturedAnnotations());
}
```

`capturedAnnotations` 는 핸들러가 알 바 아닌데도 손으로 다시 넘겨야 한다. 하나라도 빠뜨리면
조용히 정보가 사라진다(예: `dependencies` 를 빼먹으면 그 자원의 의존 관계가 통째로 날아간다).

**있으면:**

```java
public ScannedResourceState handle(AllowPort anno, ScannedResourceState state) {
    return state.withConfigEntry("port", anno.port());
}
```

즉 ①②③ 을 `ScannedResourceState` 안에 한 번만 구현해 두고, 프로바이더 작성자는 **바꾸고 싶은
것만** 말한다. 값이 여럿이면 이어 붙인다: `state.withConfigEntry("a", 1).withConfigEntry("b", 2)`.

> ✅ **확정 — 연다.** 이 메서드가 없으면 위 ③ 을 프로바이더마다 반복하게 되고, `spi` 는 프로바이더가
> 가장 자주 쓰는 표면이라 그 반복이 그대로 버그 표면이 된다.
>
> **`dependencies` 를 건드리는 헬퍼(`withDependency` 등)는 지금 열지 않는다.** 매크로 어노테이션의
> 대부분은 설정값을 넣는 일이고, 의존 관계를 더하는 핸들러가 실제로 나타나기 전에는 어떤 모양이
> 편할지 고를 근거가 없다. 필요하면 그때 6인자 생성자로 쓰다가 헬퍼로 승격한다
> (`CONVENTIONS` §8.6 의 "미루는 쪽이 안전하다"와 같은 판단).

---

## 5. 변환 알고리즘 (안 A 기준)

`create(ScannedResources scanned)`:

1. `scanned` 가 `null` 이면 거부한다(`NullPointerException`).
2. `scanned.resources()` 를 **순서 그대로** 돈다. 자원 개수는 변하지 않는다 — 이 단계는 자원을
   더하거나 빼지 않고 **1:1 로 옮긴다.** (자원 추가는 Comparator/Plan 이 다룰 개념이 아니다.)
3. 자원마다:
   - `capturedAnnotations()` **원본 목록의 스냅샷**을 순서대로 돈다.
     ⚠️ 누적 상태(`acc`)의 목록이 아니라 **원본 기준**이다. 핸들러가 돌려준 상태에 어노테이션이
     더 붙어 있어도 그건 처리하지 않는다 — 그렇지 않으면 핸들러가 자기 자신을 다시 부르게 만들
     수 있고, 종료를 보장할 수 없다. (어노테이션이 어노테이션을 낳는 기능은 이번 범위 밖.)
   - 순서는 스캐너가 정한 순서(타입 이름순)를 그대로 따른다 → **결정적**이다.
     같은 config 키를 두 어노테이션이 건드리면 **나중 것이 이긴다.** 문서에 명시한다.
   - 핸들러 인스턴스는 §3 의 캐시에서 얻는다.
4. 다 소비한 상태에서 `kind`/`logicalId`/`config`/`dependencies`/`requiredFields` 를 꺼내
   `DesiredResourceState` 로 옮긴다. `capturedAnnotations` 는 **버린다** — 다 풀었다는 것이
   `DesiredResourceState` 라는 타입의 의미다.
5. 어노테이션이 하나도 없는 자원은 3번이 빈 루프라 그대로 4번으로 간다(= 순수 타입 변환).
6. `DesiredResources` 로 묶어 돌려준다. 빈 입력 → 빈 출력.

**`requiredFields` 는 건드리지 않는다.** 필수 필드가 실제로 채워졌는지 보는 것은 `Validator` 의
일이다(`CONVENTIONS` §8.1 — 위반을 모아 보고해야 하므로 예외가 아니라 결과 객체). 이 모듈은
검증하지 않고 **변환만** 한다.

---

## 6. 타입 안전성과 실패 처리

### 6-1. 제네릭 구멍 — 여기서만 unchecked 캐스트가 난다

`CapturedAnnotation` 은 `Annotation anno` 와 `Class<? extends BehaviorHandler<?>>` 를 들고 있어,
"이 핸들러가 이 어노테이션을 받는다"는 것을 컴파일러가 알 수 없다. `handle` 을 부르려면 결국
`BehaviorHandler<Annotation>` 으로의 **비검사 캐스트**가 필요하다.

- 캐스트를 **private 메서드 하나에 가둔다**(`@SuppressWarnings("unchecked")` 도 거기 한 곳).
- 부르기 **전에** 핸들러가 선언한 타입 인자를 리플렉션으로 뽑아
  (`getGenericInterfaces()` → `BehaviorHandler` 의 실제 타입 인자) `anno.annotationType()` 과
  맞는지 본다. 안 맞으면 **부르지 않고** 예외를 던진다.
  → 안 그러면 `ClassCastException` 이 핸들러 **안에서** 터져 사용자가 원인을 못 찾는다.
- 타입 인자를 확정할 수 없는 핸들러(raw 구현, 제네릭 그대로 넘기는 구현)는 검사를 건너뛰고
  호출한다. 그때 나는 `ClassCastException` 은 맥락을 붙여 다시 던진다.

### 6-2. 전용 예외 — `internal/DesiredStateException`

`CONVENTIONS` §8.3 그대로: `internal`, unchecked, `(String)` + `(String, Throwable)`,
`serialVersionUID`, **모듈당 하나**. 이름은 선례(`ResourceScanException`, `StateStoreException`)를
따라 `DesiredStateException`.

메시지에는 **사용자가 고쳐야 할 대상**을 담는다 — 자원의 `logicalId`, 어노테이션 타입,
핸들러 FQCN. 이 셋이 없으면 "어느 자원의 어느 어노테이션이 문제인가"를 알 수 없다.

던지는 경우:

| 상황 | 왜 예외인가 |
|---|---|
| 핸들러에 public 무인자 생성자가 없다 / 추상 클래스다 / 생성자가 던졌다 | 프로바이더의 선언 실수, 진행 불가 |
| 어노테이션 타입과 핸들러 타입 인자가 다르다 | `@Behavior` 를 잘못 연결한 것 |
| 핸들러가 `null` 을 돌려줬다 (안 A) | 계약 위반 |
| 핸들러가 `logicalId` 또는 `kind` 를 바꿔 돌려줬다 (안 A) | `logicalId` 는 Comparator 의 매칭 키다. 여기서 바뀌면 뒤에서 "삭제 + 생성"으로 보인다 |
| 핸들러가 예외를 던졌다 | 원인은 `cause` 로 붙이고 맥락(어느 자원/어느 어노테이션)을 더한다 |

**첫 실패에서 멈춘다**(결과 객체가 아니라 예외). 근거는 `CONVENTIONS` §8.1 — 여기서 실패하면
뒤 단계로 넘길 값 자체를 만들 수 없다.

`dependencies` 와 `requiredFields` 변경은 **허용**한다. 어노테이션이 의존 자원을 덧붙이는 것은
정상적인 매크로의 일이다. 잠그는 것은 **식별자(kind, logicalId)** 뿐이다.

---

## 7. 이번 범위

**한다**

- `internal/DesiredStateCreator.create()` 본문 구현.
- `internal/DesiredStateException` 신설.
- `spi/BehaviorHandler.handle` 시그니처 확정 (§4 결정). **이 파일을 고치는 것은 정당하다** —
  뼈대 feature 가 명시적으로 "DesiredStateCreator 를 맡는 사람이 정한다"고 넘긴 숙제다.
- `spi/ScannedResourceState.withConfigEntry(String, Object)` 복사 헬퍼 + Javadoc (§4-1).
- 테스트용 fixture 어노테이션/핸들러 (`src/test` 아래에만 둔다 — 코어에 프로바이더 코드가 들어가지
  않는다는 `docs/plan.md` §2 를 지킨다).

**안 한다 (그리고 왜)**

- 실제 `@AllowSSH` / `AllowSshHandler` 구현 → 프로바이더 레포의 몫. 여기선 fixture 로만 흉내낸다.
- `Validator` 연결, `InfraStruct.run()` 배선 → 배선 feature 의 몫.
- `ResourceScanner` 본문 → 다른 브랜치. **이번 테스트는 `ScannedResources` 를 손으로 조립하므로
  스캐너가 스텁이어도 막히지 않는다.**
- 어노테이션이 어노테이션을 낳는 재귀 처리 (§5-3).
- 핸들러 실행 순서를 사용자가 지정하는 기능(우선순위) → 필요해지면 `@Behavior` 에 속성을 더한다.
  지금은 타입 이름순 결정성으로 충분하다.

---

## 8. 검증 관점 (spec 단계에서 행동 목록으로 바뀔 것들)

- 어노테이션이 없는 자원은 필드가 그대로 옮겨진 `DesiredResourceState` 가 된다.
- 어노테이션 하나가 `config` 에 값을 넣으면 결과 `config` 에 그 값이 있다.
- 인자 있는 어노테이션(`@AllowPort(port=22)`)의 **인자 값**이 반영된다.
- 어노테이션 둘이 순서대로 적용되고, 같은 키를 쓰면 나중 것이 이긴다.
- 자원 여러 개가 순서를 유지한 채 각자 자기 어노테이션만 소비한다.
- 빈 입력은 빈 출력이다.
- 결과 `DesiredResourceState` 에는 소비할 어노테이션이 남아 있지 않다(타입으로 보장).
- 핸들러는 자원 여러 개에서 인스턴스가 재사용된다.
- 실패들: 무인자 생성자 없음 / 타입 불일치 / `null` 반환 / 식별자 변조 / 핸들러가 던짐
  → 각각 `DesiredStateException` 이고 메시지에 logicalId 와 핸들러 FQCN 이 있다.
- 원본 `ScannedResources` 는 변하지 않는다.

## 9. 분량 예상

main 250줄 안팎(예외 클래스 포함) + test 350줄 안팎. `ResourceScanner`(1,691줄) 같은 분할이
필요한 크기는 아니라 **한 PR** 로 간다. 커지면 "변환 + 성공 경로" / "실패 처리와 진단 메시지"
두 PR 로 자른다.

## 10. 검토 결정 로그 (2026-08-20)

plan 검토에서 §10 의 열린 질문 셋이 전부 닫혔다.

| # | 질문 | 결정 | 근거 |
|---|---|---|---|
| 1 | `handle` 시그니처 A / B | **안 A** — `ScannedResourceState handle(T, ScannedResourceState)` | 검토 코멘트 *"A 안이 좋은것 같아."* + §4 |
| 2 | `withConfigEntry` 를 열 것인가 | **연다** | §4-1. 안 열면 재조립 코드가 프로바이더마다 반복되고 그게 곧 버그 표면이다 |
| 3 | `DesiredStateException` 을 `internal` 로 둘 것인가 | **`internal`** | 검토 코멘트 *"어차피 InfraStruct.run() 에서 실행되니까."* — 예외를 잡는 쪽이 엔진 자신이면 `internal` 이라는 `CONVENTIONS` §8.3 의 판별 규칙과 같은 결론 |

> #3 은 `ResourceScanException` 이 남겨 둔 같은 질문(`resource-scanner` summary 「열린 질문」 2번)과
> 짝이다. 두 예외가 독립적으로 같은 결론에 도달했으므로, 배선 feature 에서 `api` 로 올릴지 다시
> 볼 때도 **둘을 함께** 본다.

남은 열린 항목은 없다. 이 plan 으로 spec 에 들어간다.
