# feature: module-registry

브랜치: `feat/module-registry-impl` (분기 시점 = `dev`)

입력 자료: [`pleaseread.md`](./pleaseread.md), `InfraStruct.drawio` 의 **ModuleRegistry** 박스와 메모,
`dev` 의 뼈대 클래스들, 아직 머지되지 않은 다른 브랜치들(§6).

---

## 1. 목표 (무엇을)

`@InfraStructApplication(provider = "aws")` 의 **문자열 하나**를, 그 프로바이더가 만든
**Validator·Applier 객체**로 바꿔 주는 모듈 `ModuleRegistry` 를 만든다.

지금은 그 연결이 통째로 비어 있다 — `InfraStruct` 의 생성자·`run(Class)` 는 TODO 주석만 있고,
프로바이더가 `@RegisterProvider` 로 등록한 클래스를 **아무도 읽지 않는다.** 이 feature 가 그
"등록 → 조회 → 객체화 → 주입" 한 줄기를 잇는다.

## 2. 다이어그램이 확정한 계약

drawio 의 `ModuleRegistry` 박스(내부 코드 색)와 거기 달린 메모:

```
ModuleRegistry
─────────────────────────────────────
- validator: Class<? extends Validator>
- applier:   Class<? extends Applier>
- provider:  Class<? extends Provider>
─────────────────────────────────────
+ getValidator(): Validator
+ getApplier():   Applier
```

> 메모: "사용자가 사용 선언한 자원에 종속되는 validator와 applier를 찾아준다.
> 방법: `@RegisterProvider` 를 붙인 토큰을 스캔해서 사용자가 사용 선언한 프로바이더인지를
> 확인(providerId)후 validator와 applier를 가져온다."

즉 **필드는 `Class` 객체**를 들고, **접근자가 인스턴스**를 돌려준다. `Use` 화살표는
`Validator`, `Applier`, `Provider` 세 곳으로 나간다.

> **이름만 다이어그램과 다르게 간다 (검토 확정).** 접근자 이름은 `getValidator()`/`getApplier()`
> 대신 **`validator()` / `applier()`** 를 쓴다 — 이 레포의 기존 접근자(`ResourceScanner.basePackage()`)가
> `get` 접두를 쓰지 않기 때문이다. 계약(무엇을 돌려주는가)은 다이어그램 그대로다.

## 3. 동작 시나리오 (이 feature 가 완성하는 흐름)

```
사용자 코드                프레임워크
──────────                ──────────
@InfraStructApplication(provider = "aws")
public class Main {
    main() -> InfraStruct.run(Main.class)
}
                          1. Main.class 의 @InfraStructApplication 읽기 → "aws"
                          2. new InfraStruct("aws")
                          3.   new ModuleRegistry("aws")
                          4.     classpath 에서 @RegisterProvider 붙은 클래스 전부 스캔
                          5.     providerId 가 "aws" 인 토큰(Aws.class) 선택
                          6.     토큰의 validator()/applier() Class 를 필드에 보관
                          7.   registry.validator() / applier() 로 객체화
                          8.   InfraStruct 의 validator/applier 필드에 저장
                          9. run() → (파이프라인은 아직 TODO)
```

프로바이더 쪽이 해야 하는 일(다이어그램 "새로운 프로바이더 추가 방법" 1~3번)은 이미 dev 에
타입이 다 있다 — `Provider`, `@RegisterProvider`, `Validator`, `Applier`. **없는 건 읽는 쪽뿐이다.**

## 4. 설계 결정

### 4.1 패키지 — `com.infrastruct.internal`

기준은 "엔진 밖의 누가 이 타입을 코드에서 쥐는가"(`CONTRIBUTING.md` §2). 프로바이더도 사용자도
`ModuleRegistry` 를 직접 쥐지 않는다. 부르는 건 `InfraStruct` 하나뿐 → **internal**.
(다이어그램에서도 "프레임워크 내부 코드" 색이다.)

### 4.2 토큰을 찾는 방법 — classgraph 로 classpath 스캔

프로바이더 토큰(`Aws`)은 **별도 jar 의 알 수 없는 패키지**에 있다. 코어가 이름을 알면 안 되므로
(`@InfraStructApplication.provider` 가 `Class` 가 아니라 `String` 인 이유와 같다) 런타임 탐색이 필요하다.

- **채택: classgraph 로 `@RegisterProvider` 가 붙은 클래스 전수 스캔.**
  이미 `ResourceScanner` 가 쓰는 의존성이라 새로 추가할 것이 없고(`framework/build.gradle`),
  다이어그램 메모가 명시적으로 "스캔"이라고 적었다.
- 기각 — `ServiceLoader`(`META-INF/services`): 프로바이더에게 어노테이션 **말고** 서비스 파일까지
  요구하게 되어 "토큰에 어노테이션만 붙이면 끝"이라는 등록 모델이 깨진다.
- 기각 — 코어에 프로바이더 목록 하드코딩: 확장성 자체를 버리는 선택.

스캔 범위는 **classpath 전체**다. `ResourceScanner` 와 달리 basePackage 로 좁힐 수 없다 —
사용자 코드가 아니라 남의 jar 를 찾는 일이기 때문. 비용은 실행당 1회다.

### 4.3 언제 찾고, 언제 객체를 만드나

- **스캔·검증은 생성자에서 한 번.** 잘못된 설정(오타 난 provider 이름 등)은 파이프라인을 돌기
  전에 즉시 실패해야 한다. 생성자가 끝나면 세 `Class` 필드가 채워져 있음이 보장된다.
- **객체화는 접근자에서.** 다이어그램대로 필드는 `Class`, `validator()`/`applier()` 가
  리플렉션으로 인스턴스를 만든다. 프로바이더 구현체는 **public 무인자 생성자**를 요구한다
  (프레임워크가 만들어 주는 객체이므로 생성자 인자를 줄 방법이 없다).
- 호출부(`InfraStruct`)가 결과를 필드에 담아 두므로 **캐싱하지 않는다** (검토 확정). 호출마다 새
  인스턴스이며, 스캔 결과를 담는 static 캐시도 두지 않는다 — 테스트 격리를 깨는 전역 상태를 피한다.
  이 사실을 Javadoc 에 적는다.

### 4.4 `@RegisterProvider.validator()` 의 상한을 좁힌다

지금은 `Class<?>` 다. 이유는 provider-related-classes feature 시점에 `Validator` 가 없었기 때문이고,
그 Javadoc 에 *"생기면 `Class<? extends Validator>` 로 좁힌다"* 고 적혀 있다. **지금 dev 에 있다.**

- **채택: `Class<? extends Validator>` 로 좁힌다.** 좁히면 registry 가 런타임에 타입을 확인하고
  던질 실패 케이스 하나가 **프로바이더 컴파일 시점**으로 옮겨간다 — 더 이른 실패가 더 좋은 실패다.
- 비용: `RegisterProviderTest` 의 픽스처 `DummyValidator` 가 `Validator` 를 상속하도록 고쳐야 하고,
  `applierBoundIsNarrowedToApplier` 와 짝이 되는 테스트를 validator 쪽에도 추가한다. (작다)
- 충돌 위험 없음: 미머지 PR 중 `RegisterProvider.java` 를 건드리는 브랜치는 없다(§6).
- 그래도 registry 는 **방어적으로 한 번 더 확인한다** — 어노테이션은 리플렉션으로 읽는 값이고,
  프로바이더가 옛 버전 코어로 컴파일한 jar 를 섞을 수 있다.

### 4.5 실패했을 때 — `ModuleRegistryException`

컨벤션 §8: "더 진행할 수 없는 실패"는 예외, 이름은 `<모듈>Exception`, 위치는 `internal`,
`RuntimeException` 상속, 아래에서 올라온 예외는 `cause` 로 붙인다
(선례: `ResourceScanException`, `StateStoreException`).

던지는 조건 — 모두 "설정이 틀려서 아무것도 못 한다"에 해당한다:

| # | 상황 | 메시지에 담을 것 |
|---|---|---|
| 1 | providerId 가 일치하는 토큰이 classpath 에 없음 | 찾던 id + 발견된 id 목록(오타 진단용) |
| 2 | 같은 providerId 를 가진 토큰이 둘 이상 | 충돌한 클래스 이름들 |
| 3 | `@RegisterProvider` 가 `Provider` 를 상속하지 않은 클래스에 붙음 | 그 클래스 이름 |
| 4 | `validator()`/`applier()` 가 `Validator`/`Applier` 타입이 아님 | 실제 타입 |
| 5 | 구현체가 abstract·인터페이스거나 public 무인자 생성자가 없음 | 클래스 + 요구 조건 |
| 6 | 생성자가 예외를 던짐 | 원인을 `cause` 로 |
| 7 | `InfraStruct.run(mainClass)` 의 클래스에 `@InfraStructApplication` 이 없음 | 클래스 이름 |

1번과 2번을 같은 예외 타입으로 두는 건 컨벤션 §8.5("모듈당 예외 하나로 시작")를 따른 것이다.
호출부가 다르게 `catch` 할 이유가 생기면 그때 나눈다.

## 5. `InfraStruct` 배선을 어디까지 할까 — **(A) 로 확정**

`pleaseread.md` 는 "InfraStruct.run() 에서 ... 오브젝트화해서 InfraStruct 클래스 필드에 저장"까지를
이번 이야기로 적고 있다. 다만 `InfraStruct` 의 모듈 필드는 원래 7개고, 그중 5개
(ResourceScanner·DesiredStateCreator·CurrentStateStore·Comparator·PlanCreator)는 **파이프라인을 실제로
돌리는 순간** 필요해진다.

- **(A) 채택 — registry + 배선 2개까지.** (검토에서 "제안대로 진행"으로 확정)
  `ModuleRegistry` 를 만들고, `InfraStruct.run(Class)` 가 어노테이션을 읽어 생성자를 부르고,
  생성자가 registry 로 **validator·applier 두 필드만** 채운다. 나머지 5개 필드와 `run()` 본문
  파이프라인은 TODO 로 남긴다.
  - 이유: 이 feature 의 주제는 "**등록 정보를 읽어 오는 것**"이고, validator·applier 는 dev 에
    타입이 있어 지금 참조할 수 있다. 파이프라인 배선은 5개 모듈이 dev 로 머지된 뒤라야
    자기완결적으로 할 수 있다(§6).
- (B) registry 클래스만 만들고 `InfraStruct` 는 손대지 않는다 → 부르는 데가 없어 "동작한다"를
  증명하지 못한다. 리뷰어가 실제 효과를 볼 수 없다.
- (C) 파이프라인까지 전부 → 미머지 타입 5개에 의존한다. 브랜치가 자기완결적이지 않고 충돌 확정.

아래 §8·§9 는 (A) 기준이다.

부수 사항: 새 `private` 필드는 읽는 코드가 아직 없어 SpotBugs `URF_UNREAD_FIELD` 오탐이 난다.
`ResourceScanner.basePackage()` 선례대로 **package-private 접근자**를 두어 테스트가 읽게 한다
(억제 어노테이션보다 이쪽이 낫다 — 실제로 값이 들어갔는지 검증까지 된다).

## 6. 다른 브랜치와의 관계 (충돌 관리)

`dev` 와 이 브랜치는 현재 **동일**하다. 구현이 끝나 PR 이 올라간 브랜치들은 아직 머지 전이고,
이 feature 가 건드릴 파일과 겹치는 곳이 없다:

| 브랜치 (PR 올라감) | 건드리는 main 소스 | 이번 feature 와 겹침 |
|---|---|---|
| `feat/validator-common` | `spi/Validator`, `spi/ValidationResult`, `spi/Violation` | ✗ (타입 이름만 참조) |
| `feat/resource-scanner-discovery` | `internal/ResourceScanner` | ✗ |
| `feat/CurrentStateStore-impl-v2` | `internal/CurrentStateStore`, `DesiredStateCreator`, `spi/Kind` | ✗ |
| `feat/DesiredStateCreator-impl-v2` | `internal/DesiredStateCreator`, `spi/BehaviorHandler`, `ScannedResourceState` | ✗ |
| `feat/plancreator`, `feat/resource-change-comparator` | (dev 와 동일) | ✗ |

이번 feature 가 만지는 파일: **새 파일** `internal/ModuleRegistry.java`,
`internal/ModuleRegistryException.java` / **수정** `api/InfraStruct.java`,
`spi/RegisterProvider.java`(상한만), 그리고 각각의 테스트.

주의 하나: `feat/validator-common` 이 머지되면 `Validator.validate()` 의 반환 타입이
`Object` → `ValidationResult` 로 바뀐다. 이 feature 는 `validate()` 를 **호출하지 않으므로**
영향받지 않는다 — 객체를 만들어 필드에 넣기만 한다.

## 7. 이번 범위 밖 (하지 않는 것)

- `run()` 파이프라인 본문(scan → desired → validate → load → compare → plan → apply → save).
- 나머지 모듈 5개의 `InfraStruct` 필드 선언·주입.
- `BehaviorHandler` 레지스트리(매크로 어노테이션 핸들러 조회) — 다른 종류의 조회이며,
  `DesiredStateCreator` 쪽 이야기다.
- 실제 프로바이더 구현체(`Aws`, `AwsValidator`, `AwsApplier`) — 프로바이더 레포 몫.
  테스트는 테스트 소스 안의 픽스처 토큰으로 대신한다.
- 프로바이더 여러 개 동시 사용, providerId 별칭·대소문자 무시 매칭(정확히 일치만 지원).

## 8. 검증 관점 (spec 에서 테스트로 옮길 것)

정상:
1. providerId 가 일치하는 토큰을 찾아 `validator()`/`applier()` 가 등록된 구현체 타입의
   인스턴스를 돌려준다.
2. classpath 에 다른 providerId 토큰이 섞여 있어도 요청한 것만 고른다.
3. `validator()` 를 두 번 부르면 서로 다른 인스턴스다(캐싱하지 않는다는 계약).
4. `InfraStruct.run(Main.class)` 가 `@InfraStructApplication` 의 provider 로 registry 를 태워
   validator·applier 필드를 채운다.

실패(§4.5 표):
5. 없는 providerId → `ModuleRegistryException`, 메시지에 찾던 id 포함.
6. 같은 providerId 중복 → 예외.
7. `Provider` 를 상속하지 않은 토큰 → 예외.
8. 무인자 생성자 없음 / abstract → 예외.
9. 생성자가 던짐 → 예외 + `cause` 보존.
10. `@InfraStructApplication` 없는 클래스로 `run()` → 예외.

어노테이션 계약:
11. `RegisterProvider.validator()` 의 상한이 `Validator` 로 좁혀졌다(기존 applier 테스트와 대칭).

⚠️ 기존 `InfraStructTest` 3개는 "스텁이라 예외만 안 나면 된다"를 검증한다. 생성자가 실제로
registry 를 타는 순간 **깨진다** — 픽스처 프로바이더 토큰을 등록한 뒤 "정말 주입되었는가"를
검증하는 테스트로 **교체**한다. 이는 스텁이 의도한 수명(`2026-08-11-infrastructapplication-annotation/summary.md`
의 "ModuleRegistry 가 생기면 채운다")대로 진행되는 것이다.

## 9. 검토 결과 (2026-08-21, 사용자 확정)

| # | 질문 | 답 | 반영한 곳 |
|---|---|---|---|
| 1 | §5 의 (A) 범위로 갈까? | **제안대로 (A)** — registry + `InfraStruct` 배선 2필드, 파이프라인은 다음 feature | §5 |
| 2 | 접근자 이름을 다이어그램(`getValidator`)대로? | **레포 일관성 우선 → `validator()` / `applier()`** | §2, §3, §4.3, §8 |
| 3 | 스캔 결과를 static 캐시로 재사용? | **안 한다** — 전역 상태로 테스트 격리를 깨지 않는다 | §4.3 |

남은 열린 항목 없음. 다음 단계는 `/spec`.
