# spec: module-registry

[`plan.md`](./plan.md) 를 테스트 가능한 **행동 목록**으로 옮긴 것. §2 의 `- ` 불릿들이 첫 `/red` 때
체크리스트(`behaviors[]`)로 등록되고, 위에서부터 순서대로 red→green 을 돈다.

이번 feature 는 앞의 것들과 달리 **스텁이 아니라 실제 동작을 채운다**(`plan.md` §5 의 (A) 범위).
그래서 행동이 14 개로 길고 절반이 실패 케이스다. 실패 케이스가 많은 것은 이 모듈의 성격이다 —
`ModuleRegistry` 가 하는 일은 "사용자·프로바이더가 설정을 제대로 했는지 확인해서, 아니면 파이프라인을
돌기 전에 세워 두는 것"이기 때문이다.

> ⚠️ **표기 규칙.** 하네스는 이 파일의 **모든 `- ` 로 시작하는 줄**을 행동으로 등록한다
> (`.claude/harness/lib.sh` 의 `resync_behaviors`). 그래서 §2 를 제외한 나머지 절은 일부러
> 번호 목록과 표만 쓴다. 새 행동을 추가할 때는 반드시 §2 안에 `- ` 한 줄로 넣는다.

---

## 1. 확정 시그니처

### 1.1 새로 만드는 것

#### `com.infrastruct.internal.ModuleRegistry`

```java
public final class ModuleRegistry {

    // 다이어그램대로 필드는 Class 를 들고, 접근자가 인스턴스를 돌려준다.
    private final Class<? extends Provider> provider;
    private final Class<? extends Validator> validator;
    private final Class<? extends Applier> applier;

    /**
     * classpath 에서 @RegisterProvider 토큰을 전수 스캔해 providerId 가 일치하는 하나를 고르고,
     * 그 토큰이 등록한 세 Class 를 보관한다. 잘못된 설정은 전부 여기서 실패한다.
     */
    public ModuleRegistry(String providerId) { ... }

    /** 등록된 Validator 구현의 새 인스턴스. 호출할 때마다 새로 만든다(캐싱 없음, plan §4.3). */
    public Validator validator() { ... }

    /** 등록된 Applier 구현의 새 인스턴스. 호출할 때마다 새로 만든다. */
    public Applier applier() { ... }

    /** 고른 토큰 Class. 선택 결과를 테스트가 읽도록 열어 둔 package-private 접근자. */
    Class<? extends Provider> providerToken() { ... }
}
```

접근자 이름이 `getValidator()` 가 아닌 이유는 `plan.md` §9-2(레포 일관성). `providerToken()` 을 둔
이유는 §8-7 참조(SpotBugs `URF_UNREAD_FIELD` + 선택 결과 검증).

#### `com.infrastruct.internal.ModuleRegistryException`

```java
public class ModuleRegistryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ModuleRegistryException(String message) { ... }

    public ModuleRegistryException(String message, Throwable cause) { ... }
}
```

모양은 `CONVENTIONS.md` §8.3 과 선례(`ResourceScanException`)를 그대로 따른다.

### 1.2 고치는 것

#### `com.infrastruct.spi.RegisterProvider` — 상한만 좁힌다

```java
// before
Class<?> validator();
// after
Class<? extends Validator> validator();
```

`applier()` 는 이미 `Class<? extends Applier>` 라 그대로다. 근거는 `plan.md` §4.4.

#### `com.infrastruct.api.InfraStruct` — 필드 2개 + 배선

```java
public class InfraStruct {

    private final Validator validator;
    private final Applier applier;
    // 나머지 모듈 5개 필드는 이번 범위 밖 (plan §5, §7)

    /** mainClass 의 @InfraStructApplication 을 읽어 provider 를 꺼내고 실행한다. */
    public static void run(Class<?> mainClass) { ... }

    /** ModuleRegistry 로 provider 의 validator·applier 를 찾아 필드에 주입한다. */
    public InfraStruct(String provider) { ... }

    /** 파이프라인 본문은 여전히 TODO (plan §7). */
    public void run() { ... }

    /** 주입 결과를 테스트가 읽도록 열어 둔 package-private 접근자. */
    Validator validator() { ... }

    Applier applier() { ... }
}
```

### 1.3 테스트 클래스 배치

| 테스트 클래스 | 다루는 행동 | 상태 |
|---|---|---|
| `com.infrastruct.internal.ModuleRegistryTest` | 1 ~ 11 | 신규 |
| `com.infrastruct.spi.RegisterProviderTest` | 3 | 기존 수정 |
| `com.infrastruct.api.InfraStructTest` | 12 ~ 14 | 기존 **교체**(§7) |
| `com.infrastruct.fixture.provider.**` | (픽스처) | 신규, §5 |

예외 타입 자체의 테스트(행동 1·2)를 모듈 테스트 클래스 안에 두는 것은 `ResourceScannerTest` 의
`resourceScanExceptionCarriesMessage` 선례를 따른 것이다.

---

## 2. 행동 목록 (red 사이클 순서)

- ModuleRegistryException 은 RuntimeException 이고 메시지를 그대로 보존한다
- ModuleRegistryException 은 원인 예외를 cause 로 보존한다
- RegisterProvider.validator() 의 상한이 Class<? extends Validator> 로 좁혀진다
- ModuleRegistry 는 classpath 의 여러 토큰 중 providerId 가 일치하는 하나를 골라 보관한다
- validator() 와 applier() 는 등록된 구현체 타입의 인스턴스를 호출할 때마다 새로 만들어 돌려준다
- providerId 가 null 이거나 공백이면 ModuleRegistryException 을 던진다
- 일치하는 토큰이 없으면 찾던 id 와 발견된 id 목록을 담아 ModuleRegistryException 을 던진다
- 같은 providerId 토큰이 둘 이상이면 충돌한 토큰 이름을 모두 담아 ModuleRegistryException 을 던진다
- Provider 를 상속하지 않은 토큰이 선택되면 그 클래스 FQCN 을 담아 ModuleRegistryException 을 던진다
- 구현체를 인스턴스화할 수 없으면 그 클래스 FQCN 과 요구 조건을 담아 ModuleRegistryException 을 던진다
- 구현체 생성자가 던진 예외는 ModuleRegistryException 으로 감싸되 cause 로 원인을 보존한다
- InfraStruct 생성자는 provider 문자열로 ModuleRegistry 를 태워 validator·applier 필드를 채운다
- InfraStruct.run(mainClass) 는 @InfraStructApplication 의 provider 값을 registry 로 넘긴다
- @InfraStructApplication 이 없는 클래스로 run() 하면 그 클래스 FQCN 을 담아 ModuleRegistryException 을 던진다

---

## 3. 이 순서인 이유 — 각 사이클이 새로 강제하는 것

TDD 하네스는 `/green` 으로 넘어갈 때 **테스트가 실제로 실패(RED)** 해야만 통과시킨다. 그래서 행동
순서는 "각 단계에서 아직 없는 코드가 하나씩 생기도록" 잡아야 한다. 이미 만족된 행동을 뒤에 두면
RED 가 나지 않아 사이클이 막힌다.

| # | 행동 | 직전까지의 구현 상태 | 새 테스트가 실패하는 지점 |
|---|---|---|---|
| 1 | 예외 메시지 | 예외 타입 자체가 없다 | 컴파일 실패 |
| 2 | 예외 cause | `(String)` 생성자만 있다 | `(String, Throwable)` 이 없어 컴파일 실패 |
| 3 | validator 상한 | 상한이 `Class<?>` | 꺼낸 상한이 `Object` 라 단언 실패 |
| 4 | 토큰 선택 | `ModuleRegistry` 가 없다 | 컴파일 실패 |
| 5 | 객체화 | 생성자가 토큰만 고른다 | `validator()`/`applier()` 가 없어 컴파일 실패 |
| 6 | 빈 providerId | 받은 문자열을 그대로 비교한다 | `null` 이면 NPE, 공백이면 정의되지 않은 실패 → 타입 단언 실패 |
| 7 | 미발견 | 못 찾았을 때의 경로가 없다 | `ModuleRegistryException` 이 아니거나 메시지에 id 가 없다 |
| 8 | 중복 | 첫 번째 일치를 그냥 쓴다 | 예외가 아예 나지 않는다 |
| 9 | Provider 미상속 | 토큰을 `Provider` 로 좁히지 않는다 | 예외가 안 나거나 `ClassCastException` 이 그대로 올라온다 |
| 10 | 인스턴스화 불가 | `newInstance()` 를 감싸지 않는다 | `ReflectiveOperationException` 계열이 그대로 올라온다 |
| 11 | 생성자 예외 | 10 에서 reflection 예외를 한 덩어리로 감쌌다 | `cause` 가 `InvocationTargetException` 이라 단언 실패 |
| 12 | InfraStruct 주입 | 생성자 본문이 비어 있다 | `validator()`/`applier()` 접근자가 없어 컴파일 실패 |
| 13 | static run 배선 | `run(Class)` 본문이 비어 있다 | 잘못된 provider 인데도 예외가 안 난다 |
| 14 | 어노테이션 없음 | null 검사가 없다 | NPE 가 난다(`ModuleRegistryException` 아님) |

⚠️ **사이클 3 과 12 는 RED 단계에서 기존 테스트를 함께 고쳐야 한다.** `/green` 단계에서는 게이트가
`src/test` 편집을 막기 때문이다(테스트를 고쳐 통과시키는 부정행위 방지).

| 사이클 | RED 에서 같이 해야 하는 기존 테스트 수정 | 안 하면 |
|---|---|---|
| 3 | `RegisterProviderTest.DummyValidator` 를 `extends Validator` 로 | green 에서 상한을 좁히는 순간 테스트 컴파일이 깨지는데, 그때는 테스트를 못 고친다 |
| 12 | `InfraStructTest` 의 기존 3개 교체(§7) | 생성자가 registry 를 타는 순간 `"aws"` 를 못 찾아 전부 실패한다 |

---

## 4. 테스트 시나리오 (Given / When / Then)

**모든 시나리오의 공통 Given** — framework 의 test classpath 에 §5 의 픽스처 토큰이 **전부 함께**
올라와 있다. `ModuleRegistry` 는 classpath 전체를 스캔하므로(`plan.md` §4.2) 픽스처를 package 로
격리할 수 없고, **providerId 로만 격리된다**. 아래 각 표에서는 이 전제를 반복하지 않는다.

기대값의 클래스 이름은 문자열 리터럴로 박지 않고 `AlphaProvider.class.getName()` 처럼 계산해서
비교한다(`resource-scanner` spec 의 선례).

### 행동 1 — ModuleRegistryException 은 RuntimeException 이고 메시지를 보존한다

| # | Given | When | Then |
|---|---|---|---|
| 1-1 | 메시지 문자열 `"com.example.Aws 를 찾을 수 없다"` | 그 메시지로 만든 예외를 던진다 | `RuntimeException` 의 하위이고, `getMessage()` 가 준 문자열과 정확히 같다 |

### 행동 2 — 원인 예외를 cause 로 보존한다

| # | Given | When | Then |
|---|---|---|---|
| 2-1 | 원인 예외 `new NoSuchMethodException("<init>")` | 메시지와 그 원인으로 만든 예외를 던진다 | `getCause()` 가 그 인스턴스와 **동일한 객체**다(`hasCause`) |

### 행동 3 — validator() 의 상한이 Validator 로 좁혀진다

기존 `applierBoundIsNarrowedToApplier` 와 대칭인 테스트다.

| # | Given | When | Then |
|---|---|---|---|
| 3-1 | `RegisterProvider.class.getMethod("validator")` 의 제네릭 반환 타입 | 와일드카드의 상한을 꺼낸다 | 상한이 `Validator.class` 다 (좁히기 전에는 `Object.class`) |
| 3-2 | 픽스처 `DummyValidator` 를 `extends Validator` 로 고친 상태 | `TestProvider` 의 어노테이션에서 `validator()` 를 읽는다 | 여전히 `DummyValidator.class` 다 — 상한을 좁혀도 기존 계약이 그대로 성립한다 |

### 행동 4 — providerId 가 일치하는 토큰 하나를 고른다

| # | Given | When | Then |
|---|---|---|---|
| 4-1 | `AlphaProvider`(id=`alpha`)·`BetaProvider`(id=`beta`) 가 classpath 에 함께 있다 | `new ModuleRegistry("alpha")` | 예외 없이 만들어지고 `providerToken()` 이 `AlphaProvider.class` 다 |
| 4-2 | 4-1 과 같다 | `new ModuleRegistry("beta")` | `providerToken()` 이 `BetaProvider.class` 다 — "찾은 첫 토큰을 그냥 쓴다"는 구현을 반증한다 |

> 4-1 과 4-2 를 **한 사이클에 같이** 두는 이유: 토큰이 하나뿐인 상황만 검증하면 providerId 를 아예
> 보지 않는 구현도 통과한다. 그러면 뒤에 "id 로 고른다" 행동을 따로 둬도 이미 만족돼 RED 가 나지
> 않는다(§3).

### 행동 5 — 접근자가 등록된 구현체 인스턴스를 매번 새로 만든다

| # | Given | When | Then |
|---|---|---|---|
| 5-1 | `new ModuleRegistry("alpha")` | `validator()` | 반환 객체가 정확히 `AlphaValidator` 타입이다(`isExactlyInstanceOf`) |
| 5-2 | 5-1 과 같다 | `applier()` | 반환 객체가 정확히 `AlphaApplier` 타입이다 |
| 5-3 | 5-1 과 같다 | `validator()` 를 연달아 두 번 부른다 | 두 반환 객체가 **서로 다른 인스턴스**다(`isNotSameAs`) — 캐싱하지 않는다는 계약(`plan.md` §4.3, §9-3) |
| 5-4 | 5-1 과 같다 | `applier()` 를 연달아 두 번 부른다 | 두 반환 객체가 서로 다른 인스턴스다 |
| 5-5 | `new ModuleRegistry("beta")` | `validator()` | `BetaValidator` 타입이다 — 토큰과 무관하게 한 구현을 돌려주는 구현을 반증한다 |

> 5-3·5-4 를 별도 행동으로 두지 않은 이유: 5-1 을 자연스럽게 구현하면(매 호출 `newInstance()`)
> 이미 만족되어 단독으로는 RED 가 나지 않는다. 계약을 **고정**하는 테스트라 같은 사이클에 둔다.

### 행동 6 — providerId 가 비어 있으면 던진다

| # | Given | When | Then |
|---|---|---|---|
| 6-1 | — | `new ModuleRegistry(null)` | `ModuleRegistryException` 이다. `NullPointerException` 이면 실패 |
| 6-2 | — | `new ModuleRegistry("")` | `ModuleRegistryException`, 메시지에 `@InfraStructApplication` 이 들어 있다(어디를 고쳐야 하는지) |
| 6-3 | — | `new ModuleRegistry("   ")` | 6-2 와 같다 — 공백만 있는 값도 비어 있는 것으로 본다(`isBlank()` 기준) |

> 이 행동은 `plan.md` §4.5 표에 없던 것이다. 추가 근거는 §10-1.

### 행동 7 — 일치하는 토큰이 없으면 던진다

| # | Given | When | Then |
|---|---|---|---|
| 7-1 | 어떤 픽스처도 `no-such-provider` 를 선언하지 않는다 | `new ModuleRegistry("no-such-provider")` | `ModuleRegistryException`. 메시지에 **찾던 id** 인 `no-such-provider` 와, **발견된 id** 인 `alpha`·`beta` 가 모두 들어 있다(오타 진단용) |
| 7-2 | 토큰의 id 는 소문자 `alpha` 다 | `new ModuleRegistry("ALPHA")` | 예외가 난다 — 대소문자 무시 매칭을 하지 않는다(`plan.md` §7, 정확히 일치만) |
| 7-3 | `UnregisteredProvider` 는 `Provider` 를 상속하지만 `@RegisterProvider` 가 없다 | 7-1 과 같은 호출 | 발견된 id 목록 어디에도 `UnregisteredProvider` 이름이 없다 — 스캔 기준이 상속이 아니라 어노테이션임을 반증 가능하게 만든다 |
| 7-4 | `twin` id 가 두 토큰에 중복 선언돼 있다 | 7-1 과 같은 호출 | 발견된 id 목록에 `twin` 이 **한 번만** 나온다(`containsOnlyOnce`) — 진단용 목록이 중복으로 지저분해지지 않는다 |

### 행동 8 — 같은 providerId 가 둘 이상이면 던진다

| # | Given | When | Then |
|---|---|---|---|
| 8-1 | `TwinOneProvider` 와 `TwinTwoProvider` 가 둘 다 id=`twin` 이다 | `new ModuleRegistry("twin")` | `ModuleRegistryException`. 메시지에 **두 클래스의 FQCN 이 모두** 와 `twin` 이 들어 있다 — 하나만 알려 주면 사용자가 나머지를 직접 찾아야 한다 |
| 8-2 | 8-1 과 같이 중복이 classpath 에 존재한다 | `new ModuleRegistry("alpha")` | 정상적으로 만들어진다 — 검증은 **요청한 id 에 대해서만** 한다. 남의 프로바이더가 깨져 있다고 내 실행이 막히지 않는다 |

> 8-2 는 이 feature 전체의 테스트 전략이 성립하는 근거이기도 하다. 검증이 요청한 id 로 한정되기
> 때문에 깨진 픽스처들을 같은 classpath 에 올려 둘 수 있다(§4 공통 Given).

### 행동 9 — Provider 를 상속하지 않은 토큰

| # | Given | When | Then |
|---|---|---|---|
| 9-1 | `RogueToken` 에 `@RegisterProvider(providerId = "rogue")` 가 붙었지만 `Provider` 를 상속하지 않는다 | `new ModuleRegistry("rogue")` | `ModuleRegistryException`. 메시지에 `RogueToken` 의 FQCN 과 `Provider` 의 FQCN 이 들어 있다(무엇을 상속해야 하는지) |

> 이 시나리오가 "미발견(7-1)"이 아니라 **전용 메시지**로 실패한다는 것 자체가, 스캔 기준이
> `Provider` 하위 클래스 열거가 아니라 어노테이션임을 증명한다. 상속으로 스캔했다면 `rogue` 는
> 애초에 발견되지 않아 7-1 형태로 실패했을 것이다.

### 행동 10 — 구현체를 인스턴스화할 수 없다

| # | Given | When | Then |
|---|---|---|---|
| 10-1 | id=`abstract-validator` 의 validator 가 abstract 클래스다 | `validator()` | `ModuleRegistryException`. 메시지에 그 클래스 FQCN 과 `public 무인자 생성자` 요구가 들어 있다 |
| 10-2 | id=`no-ctor` 의 applier 가 인자 있는 생성자만 선언했다 | `applier()` | 10-1 과 같은 형태 — applier 경로도 같게 처리된다 |
| 10-3 | id=`private-ctor` 의 validator 는 무인자 생성자가 `private` 이다 | `validator()` | 10-1 과 같은 형태 — `setAccessible` 로 뚫지 않는다(프레임워크가 만드는 객체는 public 계약) |
| 10-4 | id=`interface-applier` 의 applier 가 `Applier` 인터페이스 그 자체다 | `applier()` | 10-1 과 같은 형태 |
| 10-5 | 위 네 가지 id 중 하나 | `new ModuleRegistry(id)` **까지만** | 생성자는 예외를 던지지 않는다 — 스캔·선택은 생성자, 객체화는 접근자라는 분담(`plan.md` §4.3) |

### 행동 11 — 구현체 생성자가 던진 예외

| # | Given | When | Then |
|---|---|---|---|
| 11-1 | id=`throwing` 의 validator 생성자가 `IllegalStateException("boom")` 을 던진다 | `validator()` | `ModuleRegistryException` 이고, `getCause()` 가 그 `IllegalStateException` 이다 |
| 11-2 | 11-1 과 같다 | 11-1 과 같다 | `getCause()` 가 `InvocationTargetException` 이 **아니다** — reflection 래퍼를 벗겨 진짜 원인을 붙인다 |
| 11-3 | 11-1 과 같다 | 11-1 과 같다 | 메시지에 `ThrowingValidator` 의 FQCN 이 들어 있다 |

> 11-2 가 이 사이클의 RED 를 만든다. 행동 10 을 `catch (ReflectiveOperationException e)` 한 덩어리로
> 구현하면 cause 가 `InvocationTargetException` 이 되어 여기서 걸린다.

### 행동 12 — InfraStruct 생성자가 두 필드를 채운다

| # | Given | When | Then |
|---|---|---|---|
| 12-1 | id=`alpha` 토큰이 등록돼 있다 | `new InfraStruct("alpha")` | 예외 없이 만들어지고, `validator()` 가 `AlphaValidator`, `applier()` 가 `AlphaApplier` 인스턴스다 |
| 12-2 | — | `new InfraStruct("no-such-provider")` | `ModuleRegistryException` 이 그대로 올라온다 — 파이프라인을 돌기 전 **생성자에서** 즉시 실패한다 |
| 12-3 | `new InfraStruct("alpha")` | `run()` | 파이프라인 본문은 아직 TODO 라 예외 없이 반환한다(`plan.md` §7) |

### 행동 13 — run(mainClass) 가 어노테이션 값을 registry 로 넘긴다

`run(Class)` 는 `void` 이고 만든 인스턴스를 밖으로 내보내지 않는다. 그래서 "정말 넘겼는가"는
**실패 경로로** 관찰한다 — 잘못된 provider 를 넣었을 때 그 값이 담긴 예외가 올라오면, 어노테이션
값이 실제로 registry 까지 흘러갔다는 뜻이다.

| # | Given | When | Then |
|---|---|---|---|
| 13-1 | `@InfraStructApplication(provider = "alpha")` 가 붙은 픽스처 메인 클래스 | `InfraStruct.run(AlphaApp.class)` | 예외 없이 반환한다 |
| 13-2 | `@InfraStructApplication(provider = "no-such-provider")` 가 붙은 픽스처 메인 클래스 | `InfraStruct.run(BrokenApp.class)` | `ModuleRegistryException` 이고 메시지에 `no-such-provider` 가 들어 있다 — 어노테이션 값이 그대로 전달됐다는 증거 |

### 행동 14 — @InfraStructApplication 이 없는 클래스

| # | Given | When | Then |
|---|---|---|---|
| 14-1 | 어노테이션이 없는 평범한 클래스 `PlainClass` | `InfraStruct.run(PlainClass.class)` | `ModuleRegistryException` 이고, 메시지에 `PlainClass` 의 FQCN 과 `@InfraStructApplication` 이 들어 있다. `NullPointerException` 이면 실패 |

---

## 5. Test fixture 설계

`framework/src/test/java/com/infrastruct/fixture/provider/` 아래에 둔다. framework 는 프로바이더를
의존하지 않으므로, 실제 프로바이더(`Aws`, `AwsValidator`, `AwsApplier`)를 흉내 낸 토큰 계층을 테스트
소스 안에 직접 만든다(`plan.md` §7).

> **모두 public top-level 클래스로 만든다.** registry 가 다른 package 에서 리플렉션으로 구현체를
> 인스턴스화하는데, package-private 클래스는 `getConstructor()` 단계에서 걸려 "인스턴스화 불가"와
> 구분되지 않는 실패를 낸다. 테스트가 무엇을 검증하는지 흐려지므로 처음부터 public 으로 둔다.

### 5.1 정상 경로

| 클래스 | 내용 | 무엇을 위한 것인가 |
|---|---|---|
| `AlphaProvider extends Provider` | `@RegisterProvider(providerId = "alpha", validator = AlphaValidator.class, applier = AlphaApplier.class)` | 정상 경로 전체 |
| `AlphaValidator extends Validator` | public 무인자 생성자 | 인스턴스 타입 검증 대상 |
| `AlphaApplier implements Applier` | `apply` 는 받은 `current` 를 그대로 반환 | 인스턴스 타입 검증 대상 |
| `BetaProvider extends Provider` | id=`beta`, `BetaValidator`/`BetaApplier` | **선택**이 실제로 id 로 이뤄지는지(4-2, 5-5) |
| `BetaValidator`, `BetaApplier` | alpha 와 **다른 타입**이어야 한다 | 같은 타입이면 5-5 가 반증력을 잃는다 |
| `UnregisteredProvider extends Provider` | 어노테이션 **없음** | 상속만으로는 발견되지 않는다(7-3) |

### 5.2 깨진 설정 — id 로 격리한다

classpath 를 공유하므로 package 로는 격리할 수 없다. 대신 **깨진 픽스처마다 고유한 providerId** 를
주고, 검증을 "요청한 id 에 대해서만" 하게 만들어 서로를 오염시키지 않는다(8-2).

| providerId | 클래스 | 깨뜨린 지점 | 검증 시나리오 |
|---|---|---|---|
| `twin` | `TwinOneProvider`, `TwinTwoProvider` | 같은 id 를 두 토큰이 선언 | 8-1, 7-4 |
| `rogue` | `RogueToken` | `@RegisterProvider` 는 붙었으나 `Provider` 미상속 | 9-1 |
| `abstract-validator` | `AbstractFixtureValidator extends Validator` (abstract) | 추상 클래스 | 10-1 |
| `no-ctor` | `NoCtorApplier implements Applier` | 인자 있는 생성자만 선언 | 10-2 |
| `private-ctor` | `PrivateCtorValidator extends Validator` | 무인자 생성자가 private | 10-3 |
| `interface-applier` | (`applier = Applier.class`) | 인터페이스 그 자체를 등록 | 10-4 |
| `throwing` | `ThrowingValidator extends Validator` | 생성자가 `IllegalStateException("boom")` 을 던짐 | 11-1 |

깨진 픽스처의 **나머지 자리**(예: `no-ctor` 토큰의 validator)는 정상인 `AlphaValidator` 를 재사용한다.
한 토큰에 두 가지를 동시에 깨뜨리면 어느 쪽이 예외를 냈는지 구분할 수 없다.

### 5.3 행동 13·14 용 픽스처

어노테이션을 **읽기만** 하고 인스턴스화하지 않으므로, `InfraStructTest` 안의 nested static 클래스로
둔다(기존 `SampleApp` 자리).

| 클래스 | 내용 |
|---|---|
| `AlphaApp` | `@InfraStructApplication(provider = "alpha")` |
| `BrokenApp` | `@InfraStructApplication(provider = "no-such-provider")` |
| `PlainClass` | 어노테이션 없음 |

### 5.4 ⚠️ 이미 classpath 에 있는 토큰 하나

`RegisterProviderTest.TestProvider` 에 **`providerId = "aws"`** 가 이미 붙어 있고, 이것도 스캔에
잡힌다. 따라서:

1. "존재하지 않는 id" 시나리오에 **`"aws"` 를 쓰면 안 된다**. `no-such-provider` 를 쓴다.
2. 7-1 이 단언하는 발견된 id 목록에는 `aws` 도 섞여 나온다. 단언은 `alpha`·`beta` 포함 여부로 한다.
3. 기존 `InfraStructTest` 가 쓰던 `provider = "aws"` 는 `alpha` 로 바꾼다(§7).

`TestProvider` 의 id 자체를 바꾸지는 않는다 — 그 테스트의 관심사는 어노테이션 계약이지 registry 가
아니고, 지금 바꾸면 이번 feature 와 무관한 diff 가 늘어난다.

---

## 6. 예외 메시지 계약

`CONVENTIONS.md` §8.3 — 메시지에는 "사용자가 고쳐야 할 대상"을 반드시 담는다. 아래는 그것을
테스트로 고정하는 표다. 전체 문장을 `hasMessage` 로 박지 않고 **포함 여부**로 단언한다(문구는
바뀔 수 있지만 담긴 정보는 계약이다).

| 실패 | 메시지에 반드시 담기는 것 | 단언 방법 |
|---|---|---|
| providerId 가 비어 있음 | `@InfraStructApplication` (어디를 고쳐야 하는지) | `hasMessageContaining` |
| 미발견 | 찾던 id + classpath 에서 발견된 id 목록(중복 제거·사전순) | `hasMessageContaining` 3회, `containsOnlyOnce` |
| 중복 | 충돌한 두 토큰의 FQCN 전부 + 문제의 id | `hasMessageContaining` |
| `Provider` 미상속 | 토큰 FQCN + `Provider` FQCN | `hasMessageContaining` |
| 인스턴스화 불가 | 구현체 FQCN + `public 무인자 생성자` | `hasMessageContaining` |
| 생성자가 던짐 | 구현체 FQCN, 그리고 `cause` 에 원래 예외 | `hasMessageContaining` + `hasCauseInstanceOf` |
| `@InfraStructApplication` 없음 | 메인 클래스 FQCN + 어노테이션 이름 | `hasMessageContaining` |

---

## 7. 기존 테스트 교체 목록

`plan.md` §8 의 ⚠ 항목을 구체화한 것이다.

| 파일 | 지금 | 이번에 | 사이클 |
|---|---|---|---|
| `InfraStructTest.constructsWithProvider` | `new InfraStruct("aws")` 가 예외만 안 나면 통과 | 12-1 로 교체 — 실제로 주입됐는지 본다 | 12 |
| `InfraStructTest.instanceRunDoesNotThrow` | 스텁이라 통과 | 12-3 으로 유지하되 provider 를 `alpha` 로 | 12 |
| `InfraStructTest.staticRunDoesNotThrow` | 본문이 비어 통과 | 13-1 로 교체(같은 형태지만 이제 실제로 registry 를 탄다) | 13 |
| `InfraStructTest.SampleApp` | `provider = "aws"` | `AlphaApp`(=`alpha`)·`BrokenApp`·`PlainClass` 로 확장 | 12~14 |
| `RegisterProviderTest.DummyValidator` | 아무 클래스 | `extends Validator` | 3 |

교체는 "스텁이 의도한 수명대로" 진행되는 것이다
(`docs/features/2026-08-11-infrastructapplication-annotation/summary.md` 의 "ModuleRegistry 가
생기면 채운다").

---

## 8. 구현 힌트 (green 에서 참고)

> 행동이 아니라 구현 메모다. 여기 적힌 것이 테스트를 통과시키는 유일한 방법은 아니다.

1. **스캔**: `try (ScanResult sr = new ClassGraph().enableClassInfo().enableAnnotationInfo().scan())`
   → `sr.getClassesWithAnnotation(RegisterProvider.class).loadClasses()`. `ScanResult` 는
   `Closeable` 이라 try-with-resources 로 닫는다.
2. **어노테이션 값 읽기**: classgraph 의 `AnnotationInfo` 대신 `token.getAnnotation(RegisterProvider.class)`
   로 다시 읽는다. `Class` 값이 그대로 나와 변환이 없다.
3. **토큰 좁히기**: `token.asSubclass(Provider.class)` 의 `ClassCastException` 을 잡아 예외로 바꾼다
   (행동 9). `Validator`/`Applier` 쪽도 `isAssignableFrom` 으로 한 번 더 방어한다(`plan.md` §4.4 —
   옛 코어로 컴파일된 jar 대비, §9-1 참조).
4. **객체화**: `impl.getConstructor().newInstance()`. `getConstructor()` 는 public 생성자만 찾으므로
   "public 무인자 생성자를 요구한다"는 계약이 코드에 그대로 드러난다. `setAccessible` 은 부르지 않는다.
   `NoSuchMethodException`(없음·private·인터페이스)과 `InstantiationException`(abstract)은 행동 10,
   `InvocationTargetException` 은 `getTargetException()` 을 cause 로 붙여 행동 11.
5. **발견된 id 목록**: 중복 제거 + 사전순 정렬 후 `String.join(", ", ...)`. 정렬하지 않으면 스캔
   순서에 따라 메시지가 흔들려 7-4 가 불안정해진다.
6. **캐싱 금지**: 인스턴스를 필드에 담지 않는다. 스캔 결과를 담는 static 캐시도 두지 않는다
   (`plan.md` §9-3 — 전역 상태가 테스트 격리를 깬다).
7. **SpotBugs**: `provider` 필드는 읽는 코드가 없어 `URF_UNREAD_FIELD` 오탐이 난다. 억제
   어노테이션 대신 package-private `providerToken()` 접근자를 둔다 — `ResourceScanner.basePackage()`
   선례이고, 값이 실제로 들어갔는지 검증까지 된다(`plan.md` §5).
8. **Checkstyle**: 한 줄짜리 `if` 도 중괄호, 문자열 비교는 상수를 앞에(`"x".equals(y)`), 스타 임포트
   금지(`CONVENTIONS.md` §5).

---

## 9. 이번 범위에서 검증하지 않는 것

1. **`@RegisterProvider.validator()` 가 `Validator` 가 아닌 타입을 가리키는 경우**
   (`plan.md` §4.5 표의 4번). 어노테이션 멤버의 제네릭은 class 파일에서 지워지므로, 옛 코어로
   컴파일한 프로바이더 jar 는 런타임에 아무 `Class` 나 돌려줄 수 있다 — 그래서 **방어 코드는 남긴다**
   (§8-3). 하지만 Java 소스로는 그런 어노테이션을 만들 수 없다(상한이 컴파일을 막는다). `Proxy` 로
   가짜 어노테이션을 만들어 package-private seam 에 넣으면 가능하지만, 그 seam 은 오직 테스트
   때문에 존재하게 된다. `resource-scanner` spec 이 "필드 값을 읽지 못함"을 뺀 것과 같은 판단으로,
   커버리지 손실을 감수하고 행동 목록에서 뺀다.
2. **경계에서의 사용자용 메시지 변환**(`CONVENTIONS.md` §8.5). 이번에는 `ModuleRegistryException` 을
   그대로 올린다 — 테스트가 타입과 메시지를 직접 본다. `Error: ...` 한 줄로 바꾸는 것은 파이프라인
   배선 feature 의 몫이다.
3. `run()` 파이프라인 본문과 나머지 모듈 5개 필드(`plan.md` §7).
4. `InfraStruct.run(null)`. `NullPointerException` 이 나는 것이 맞고, 가드를 두면 "사용자가 고칠
   대상"이 없는 메시지만 늘어난다.
5. 스레드 안전성·동시 스캔. 실행당 1회 호출이라 계약할 것이 없다.
6. 스캔 성능(실행당 1회 비용), 프로바이더 여러 개 동시 사용, id 별칭·대소문자 무시 매칭
   (`plan.md` §7).
7. 실제 프로바이더 구현체(`Aws`, `AwsValidator`, `AwsApplier`) — 프로바이더 레포 몫.

---

## 10. plan 대비 더하거나 조정한 것 (검토 포인트)

| # | 무엇 | 왜 |
|---|---|---|
| 1 | **행동 6(빈 providerId)을 추가**했다. `plan.md` §4.5 표에는 없던 8번째 실패 조건이다 | `@InfraStructApplication(provider = "")` 는 사용자가 실제로 낼 수 있는 오타다. 가드가 없으면 `null` 은 NPE 로, 공백은 "발견된 id 목록"만 잔뜩 뱉는 메시지로 끝나 어디가 틀렸는지 알 수 없다 |
| 2 | `plan.md` §8-3(두 번 부르면 다른 인스턴스)을 **별도 행동이 아니라 행동 5 의 시나리오**로 넣었다 | 자연스러운 구현이 이미 만족해 단독으로는 RED 가 나지 않는다(§3). 계약을 고정하는 테스트로 같은 사이클에 둔다 |
| 3 | `plan.md` §8-2(다른 id 가 섞여도 요청한 것만 고른다)를 **행동 4 에 합쳤다** | 같은 이유다. 토큰이 하나뿐인 상황을 먼저 통과시키면 id 로 고르는 코드가 없어도 green 이 되어, 뒤 행동에서 RED 를 만들 수 없다 |
| 4 | `Class` 필드 3개 중 **`provider` 만 접근자를 둔다**(`providerToken()`) | validator·applier 는 접근자가 필드를 읽으므로 SpotBugs 오탐이 없다. 테스트 전용 접근자를 3개까지 늘리지 않고, 보관 여부는 인스턴스 타입으로 검증한다(5-1, 5-2) |
| 5 | `plan.md` §4.5 표 4번(validator 타입 불일치)을 **행동 목록에서 뺐다**. 코드에는 남긴다 | §9-1 |
| 6 | 깨진 픽스처를 package 가 아니라 **providerId 로 격리**한다 | classpath 전체 스캔이라 package 격리가 불가능하다. 대신 "검증은 요청한 id 에 대해서만"이라는 설계를 8-2 로 못 박아, 이 격리가 성립함을 테스트가 보증하게 했다 |

---

## 11. 승인 후 다음 단계

`bash .claude/harness/approve.sh` (사용자가 직접) → `/red` 로 행동 1부터 시작한다.
