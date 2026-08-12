# feature: resource-state-classes

브랜치: `feat/resource-state-classes`
담당: 선현진 · WBS `자원의 상태 관리 클래스 개발` (2026-08-12 → 08-13)

## 1. 목표 (무엇을)

파이프라인의 각 단계가 주고받는 **자원 상태 타입 7개**를 만든다.

| # | 타입 | 종류 | 한 줄 설명 |
|---|---|---|---|
| 1 | `ResourceState` | abstract class | 자원 상태의 공통 부모. kind·logicalId·config·dependencies·requiredFields. |
| 2 | `ScannedResourceState` | class | 스캔 직후의 상태. 발견한 매크로 어노테이션을 함께 들고 있다. |
| 3 | `DesiredResourceState` | class | 어노테이션을 소비해 config 에 반영한, 사용자가 원하는 최종 상태. |
| 4 | `CurrentResourceState` | class | 마지막으로 apply 된 실제 상태. 클라우드가 발급한 physicalId 포함. |
| 5 | `ScannedResources` | record | `ScannedResourceState` 목록. |
| 6 | `DesiredResources` | record | `DesiredResourceState` 목록. |
| 7 | `CurrentResources` | record | `CurrentResourceState` 목록. |

설계 노트(다이어그램) 원문: *"자원의 상태를 담는 클래스들로, 직렬화 및 역직렬화가 가능하고,
확장 가능성, 그리고 범용성을 염두하여 설계"*

**이번 작업은 "그릇"만 만든다.** 그릇을 채우고 비우는 로직(ResourceScanner,
DesiredStateCreator, CurrentStateStore)은 전부 다른 작업이다. §5 참조.

맥락: 이 7개는 WBS 상 **6개 후속 작업의 공통 선행**이다 (리소스 변경 클래스·ResourceScanner·
DesiredStateCreator·CurrentStateStore·Validator·Comparator). 그래서 계약을 넓게 잡기보다
**빨리 확정해 공유하는 것**이 이 작업의 목적이다.

## 2. 각 타입의 계약 (다이어그램 기준 + 결정)

### `ResourceState` (abstract)

```java
public abstract class ResourceState {
    private Kind kind;                     // 자원의 종류 (프로바이더 Kind enum 구현체)
    private String logicalId;              // 자원의 내부 id — @Resource(name) 값
    private Map<String, Object> config;    // 설정 모음
    private List<String> dependencies;     // 의존 자원의 logicalId 목록
    private Set<String> requiredFields;    // @Required 가 붙은 필드 이름
}
```

- **`abstract` 인 이유**: 세 하위 타입 중 하나로만 존재한다. 직접 인스턴스화할 자리가 없다.
  (`Provider` 선례와 같은 판단.)
- **`config` 에는 스칼라 값만 넣는다.** 다른 자원을 가리키는 필드는 `config` 가 아니라
  `dependencies` 로 간다 — 노트에 명시돼 있다. 자원 참조를 config 에 섞으면 Comparator 가
  "값이 바뀐 것"과 "의존 관계가 바뀐 것"을 구분하지 못한다.
- **`requiredFields` 는 Scanned·Desired 만 쓴다. Current 는 쓰지 않는다.** 노트 명시.
  이미 apply 된 실제 상태에는 "필수 여부"라는 개념이 없기 때문. 다만 필드는 부모에 두고
  Current 가 비워 두는 쪽을 택한다 — 계층을 쪼개면 얻는 것보다 복잡도가 크다.

### `ScannedResourceState`

```java
public class ScannedResourceState extends ResourceState {
    private List<CapturedAnnotation> capturedAnnotations;
}
```

- 다이어그램 박스에는 `capturedAnnotation`(단수)로 적혀 있으나 노트 본문은
  `capturedAnnotations`(복수)다. **복수를 따른다** — `List` 이므로.

### `DesiredResourceState`

```java
public class DesiredResourceState extends ResourceState { }
```

- 추가 필드 없음. **그래도 별도 타입으로 두는 이유**: 파이프라인 시그니처가
  `Validator.validate(DesiredResources)` 처럼 이 타입을 이름으로 요구한다. "검증을 통과할
  대상"과 "스캔 직후 날것"을 타입으로 구분하는 것이 이 클래스의 존재 이유다.

### `CurrentResourceState`

```java
public class CurrentResourceState extends ResourceState {
    private String physicalId;             // 클라우드가 실제로 발급한 id
}
```

### 컨테이너 3개 — `record` 로 만든다

```java
public record ScannedResources(List<ScannedResourceState> scannedResources) {}
public record DesiredResources(List<DesiredResourceState> desiredResources) {}
public record CurrentResources(List<CurrentResourceState> currentResources) {}
```

- **상태 4개는 상속 계층이라 record 를 못 쓰지만, 컨테이너 3개는 상속이 없어 record 가 된다.**
  `CapturedAnnotation` 선례와 같은 이유로 record 가 낫다 (§4).
- 파이프라인이 컨테이너를 **바꿔 끼우지 덧붙이지 않는다** — `Applier.apply()` 는 새
  `CurrentResources` 를 반환하고, Comparator 는 읽기만 한다. 그래서 불변으로 둬도 된다.
- ⚠️ 다이어그램의 필드명은 `ScannedResources` 처럼 대문자로 시작하는데, Checkstyle
  네이밍 규칙(lowerCamel) 위반이라 빌드가 깨진다. 위처럼 소문자로 시작한다.
- Gson 은 2.10 부터 record 를 지원한다. 현재 `build.gradle` 은 2.11.0 이라 문제없다.

### 가변성 — 상태 4개는 **가변**이어야 한다 (도출된 제약)

`BehaviorHandler` 의 시그니처가 이걸 강제한다:

```java
void handle(T annotation, ScannedResourceState state);   // 반환값이 void
```

`void` 라는 건 **핸들러가 넘겨받은 state 를 직접 고친다**는 뜻이다. 매크로 어노테이션의 효과가
`config` 에 반영되려면 `config` 는 살아 있는 가변 Map 이어야 한다. 따라서:

- `config` · `dependencies` · `requiredFields` 는 가변 컬렉션으로 두고, 접근자는 **살아 있는
  컬렉션을 그대로 반환**한다 (방어 복사 하지 않는다).
- 이건 의도된 설계다. §4 의 SpotBugs 항목과 직결된다.

## 3. 패키지 배치 (확정)

**확정: 7개 모두 `spi`.** (담당자 결정, 2026-08-12)

다이어그램은 이 7개를 "프레임워크 내부 코드(프로바이더는 사용하지 않는 것)" 색으로 칠해 뒀다.
그런데 `CONTRIBUTING.md` §2 의 판별 규칙은 **"엔진 밖의 누군가가 이 타입을 코드에서 직접
쥐는가?"** 이고, 실제 SPI 시그니처가 이 타입들을 그대로 노출한다:

| SPI 타입 (프로바이더·확장 작성자가 구현) | 시그니처에 등장하는 상태 타입 |
|---|---|
| `BehaviorHandler.handle(T, ScannedResourceState)` | `ScannedResourceState` |
| `Validator.validate(DesiredResources)` | `DesiredResources` |
| `Applier.apply(OrderedResourceChangeSet, CurrentResources)` | `CurrentResources` |

`internal` 에 두면 **프로바이더가 `internal` 을 import 해야 하고**, "internal 은 바꿔도 아무도
안 깨진다"는 전제가 그 순간 무너진다. 다이어그램 색은 "프로바이더가 직접 *만들지는* 않는다"는
뜻으로 읽히며, 배치 규칙과는 다른 축이다.

### 이 결정이 `CapturedAnnotation` 까지 끌고 온다 (함께 처리)

`ScannedResourceState` 가 `spi` 로 가면, 그 필드 타입인 `CapturedAnnotation`(현재 `internal`)도
`spi` 로 가야 한다. **`spi` → `internal` 의존은 방향이 거꾸로다.** 마침 이전 feature 문서가
이 배치를 "확인받고 싶다"고 남겨 뒀으니(`2026-08-11-annotation-handler-classes/plan.md` §3)
같이 정리한다.

따라서 이번 PR 에서 `internal/CapturedAnnotation.java` → `spi/CapturedAnnotation.java` 로 옮기고,
`internal/package-info.java` 만 남는 상태가 된다. `2026-08-11-annotation-handler-classes/plan.md`
§3 이 남겨 둔 열린 항목을 여기서 닫는다.

리스크는 낮다: **아직 외부 프로바이더가 하나도 없으므로** 나중에 뒤집어도 깨질 소비자가 없다.

### Kind 직렬화 — `Kind` 를 유지한다 (확정)

`CurrentStateStore` 는 `CurrentResources` 를 Gson 으로 JSON 왕복시키는데, `kind` 의 선언
타입은 인터페이스 `Kind` 이고 실제 값은 프로바이더 enum(예: `AwsKind.EC2`)이다. Gson 은
역직렬화할 구체 타입을 모른다.

**이번 작업은 다이어그램대로 `Kind kind` 로 간다.** JSON 표현을 어떻게 만들지는
`CurrentStateStore`(한정연, 08-14 시작)에서 `TypeAdapter` 로 푸는 것이 정석이고, 그러면 이쪽
필드 모양은 바뀌지 않는다. 여기서 해법을 설계하지 않는다.

실제로 확인한 Gson 2.11 동작 (JDK 21):

```
직렬화   OK  {"kind":"EC2","logicalId":"myEc2","config":{...}}
역직렬화 실패 JsonIOException: Interfaces can't be instantiated! ... Interface name: Kind
```

**쓰는 쪽은 이미 문자열이다.** Gson 이 enum 을 이름 문자열로 직렬화하므로 wire format 은 손댈
게 없고, 읽는 쪽만 없다. 이건 IaC 도구들의 공통 관례와 이미 일치한다 — Terraform 은
`"type": "aws_instance"`, Pulumi 는 `"type": "aws:ec2/instance:Instance"`, Kubernetes 는
`kind` 문자열 + Scheme 레지스트리로 해석한다. **언어 타입 정보를 JSON 에 박는 도구는 없다.**

Java 쪽 일반 해법인 `RuntimeTypeAdapterFactory`(Jackson 의 `@JsonTypeInfo` 격)는 여기선
필요 없다 — `Kind.value()` 자체가 이미 문자열 판별자이기 때문이다. (게다가 그건 gson core 가
아니라 `gson-extras` 라 클래스를 복사해 와야 한다.)

**→ 결론: `Kind kind` 를 유지한다.** 읽는 쪽은 `CurrentStateStore` 가 작은
`TypeAdapter<Kind>` 로 푼다. 앱이 `@InfraStructApplication(provider=...)` 로 프로바이더를
**하나만** 선언하므로, 복원은 "providerId → 그 프로바이더의 Kind enum → `value()` 로 매칭"이다.

⚠️ **한정연에게 미리 알릴 것** (08-14 시작 전): 이 어댑터는 프로바이더 컨텍스트가 필요하므로
`CurrentStateStore` 가 독립적인 JSON 리더가 아니라 해석된 프로바이더(또는 `ModuleRegistry`)에
의존하게 된다. 상태 파일에 `provider` 문자열도 같이 저장해 두면 파일이 자기 설명적이 되고
디버깅에도 유리하다.

## 4. SpotBugs 관련 (이번에 정책이 정해진다)

`ProviderResource` 의 억제 주석이 *"국소 예외로만 둔다. 상태 클래스들에서 같은 패턴이
반복되면 프로젝트 정책으로 재검토"* 라고 예고해 둔 바로 그 상황이다.

| 패턴 | 어디서 터지나 | 대응 |
|---|---|---|
| `UUF_UNUSED_PUBLIC_OR_PROTECTED_FIELD` | public 필드로 두면 이 모듈에서 아무도 안 읽어 오탐 | **private 필드 + 접근자**로 해결. 억제 불필요. |
| `EI_EXPOSE_REP` / `EI_EXPOSE_REP2` | 접근자가 가변 컬렉션을 그대로 반환/저장 | §2 에서 **의도한 설계**다. 좁게 `@SuppressFBWarnings` + 사유 주석. |

- `ProviderResource` 처럼 public 필드로 가면 UUF 억제가 4개 클래스에 번진다. **private +
  접근자 쪽이 억제를 하나 줄인다** (UUF 가 아예 안 남).
- `EI_EXPOSE_REP` 억제가 싫다면 대안은 명시적 조작 메서드(`putConfig`, `addDependency` …)를
  두는 것인데, 그릇 하나에 API 가 크게 늘어난다. **억제 + 사유 주석을 택한다.**
- 컨테이너 3개는 record 라 UUF 가 원천적으로 안 난다 (`CapturedAnnotation` 과 같은 이유).

## 5. 이번 범위 밖 (하지 않는 것)

| 하지 않는 것 | 누구 / 언제 |
|---|---|
| `ResourceScanner` — 그릇을 채우는 쪽 | 선현진, 08-14 (별도 작업) |
| `DesiredStateCreator` — Scanned → Desired 변환, 어노테이션 소비 | 한정연, 08-14 |
| `CurrentStateStore` — JSON 직렬화/역직렬화, `Kind` TypeAdapter | 한정연, 08-14 |
| `Validator`, `PlanCreator` | 권건우 |
| `Comparator`, `ResourceChange` 계열 | 강현서 (`feat/resource-change-comparator` 진행 중) |

## 6. 함께 처리하는 후속 (같은 PR)

`ScannedResourceState` 가 생기므로 예고돼 있던 자리표시자를 좁힌다:

```java
// spi/BehaviorHandler.java
void handle(T annotation, Object state);              // 지금
void handle(T annotation, ScannedResourceState state); // 이번 PR
```

`2026-08-11-annotation-handler-classes/summary.md` 의 "다음" 항목이다. Javadoc 의 *"아직 다른
영역에서 구현 중이라 참조할 수 없다"* 문구도 함께 지우고, `BehaviorHandlerTest` 픽스처를
새 시그니처에 맞춘다.

## 7. 검증 관점 (spec 에서 테스트로)

- 하위 3종이 부모의 5개 필드를 상속하고, 접근자가 넣은 값을 그대로 돌려준다.
- `ScannedResourceState` 가 `capturedAnnotations` 를, `CurrentResourceState` 가 `physicalId` 를
  각각 보유한다.
- `config` 가 **가변**이다 — 밖에서 얻은 Map 에 넣은 값이 상태 객체에 반영된다.
  (`BehaviorHandler.handle` 이 동작하기 위한 전제)
- 컨테이너 record 3종이 목록을 그대로 돌려준다.
- `BehaviorHandler` 구현체가 `handle(anno, ScannedResourceState)` 로 상태를 고칠 수 있다
  (§6 의 좁힌 시그니처가 실제로 쓰인다는 확인).
