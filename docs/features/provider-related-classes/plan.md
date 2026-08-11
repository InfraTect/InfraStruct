# feature: provider-related-classes

브랜치: `feat/Provider-Related-Classes-Impl`

## 1. 목표 (무엇을)

프로바이더 확장에 필요한 **6개 타입**을 만든다.

| # | 타입 | 종류 | 한 줄 설명 |
|---|---|---|---|
| 1 | `Kind` | interface | 자원의 종류. 프로바이더가 enum 으로 구현. |
| 2 | `Provider` | class (base) | 프로바이더 토큰의 부모. `class Aws extends Provider {}`. |
| 3 | `ProviderResource` | class (base) | 모든 자원의 루트. `kind`, `provider` 보유. |
| 4 | `@RegisterProvider` | annotation | 프로바이더 토큰에 붙여 모듈을 등록. **미구현 의존성 있음(§4)**. |
| 5 | `@Resource` | annotation | 자원 클래스에 붙여 자원임을 표시 + logicalId(name). |
| 6 | `@Required` | annotation | 자원 필드에 붙여 필수 필드임을 표시. |

## 2. 각 타입의 계약 (다이어그램 기준)

> 다이어그램은 **공개 필드/시그니처만** 확정한다. private 필드는 구현 재량.

### `Kind`
```java
public interface Kind {
    String value();   // 자원 종류 이름. 프로바이더가 enum 으로 구현.
}
```
- 용도: 프로바이더가 `enum AwsKind implements Kind { ... }` 형태로 상속.

### `Provider`
```java
public abstract class Provider {}   // 멤버 없음 — 확장용 토큰 베이스
```
- 다이어그램에 정의된 필드/메서드 없음. `class Aws extends Provider {}` 처럼 상속만 함.
- `abstract`: 직접 인스턴스화할 이유가 없다.

### `ProviderResource`
```java
public abstract class ProviderResource {
    public Kind kind;                          // 자원 종류(구현체 enum 저장)
    public Class<? extends Provider> provider; // 어느 프로바이더인지
}
```
- 모든 자원의 루트. 하위(AwsResource → AwsEc2)가 `provider`, `kind` 를 채운다.
- 필드 공개 범위는 다이어그램의 `+`(public)를 따른다.

### `@RegisterProvider`  ⚠️ 미구현 의존성
```java
@Retention(RUNTIME)
@Target(TYPE)
public @interface RegisterProvider {
    String providerId();
    Class<?> resourceScanner();   // TODO: Class<? extends ResourceScanner>
    Class<?> applier();           // TODO: Class<? extends Applier>
}
```
- RUNTIME/TYPE: ModuleRegistry 가 프로바이더 토큰에서 리플렉션으로 읽는다.
- `resourceScanner`/`applier` 의 정확한 상한 타입은 §4 에서 결정.

### `@Resource`
```java
@Retention(RUNTIME)
@Target(TYPE)
public @interface Resource {
    String name();   // logicalId
}
```

### `@Required`
```java
@Retention(RUNTIME)
@Target(FIELD)
public @interface Required {}   // 마커
```

## 3. 패키지 배치 (확정)

규칙: "엔진 밖의 누가 이 타입을 코드에서 쥐는가" → 사용자=api, 프로바이더=spi.

| 타입 | 패키지 | 근거 |
|---|---|---|
| `Kind`, `Provider`, `ProviderResource`, `@RegisterProvider`, `@Required` | **spi** | 프로바이더가 상속/구현/등록/자원 템플릿에 사용. |
| `@Resource` | **api** | 최종 사용자가 자기 자원 클래스에 붙임(`@Resource MyEc2`). |

## 4. ⚠️ 핵심 논점: `@RegisterProvider` 의 미구현 의존성

`@RegisterProvider` 는 `resourceScanner(): Class<? extends ResourceScanner>` 와
`applier(): Class<? extends Applier>` 를 갖는다. 그런데 `ResourceScanner`·`Applier` 는
**아직 없다** (이 브랜치 밖). 게다가 그 인터페이스들은 다시 `ScannedResources`,
`OrderedResourceChangeSet`, `CurrentResources` 같은 **또 다른 미구현 타입**에 의존한다
(연쇄 의존). 따라서 지금 제대로 만들려면 미구현 타입이 줄줄이 필요하다.

**결정: (A) `Class<?>` + TODO.** 상한 없이 받아두고, `ResourceScanner`/`Applier` 가 생기면
`Class<? extends ResourceScanner>` / `Class<? extends Applier>` 로 좁힌다.

- 이유: 이 브랜치가 **자기완결적**이어야 한다. 남이 소유한 타입(ResourceScanner/Applier)을
  여기서 만들면 병합 충돌이 나고, 연쇄 의존 때문에 어차피 빈 껍데기밖에 못 만든다.
- InfraStruct feature 와 동일한 원칙("아직 없는 타입은 참조하지 않는다").
- 프로바이더 코드가 아직 없으므로, 나중에 상한을 좁혀도 깨질 사용처가 없다.
- 각 속성에 "왜 `Class<?>` 인지 + 나중에 좁힐 것"을 주석으로 남긴다.

(기각한 대안: spi 에 빈 마커 인터페이스를 임시 생성 → 진짜 주인과 충돌 위험 + 갈아엎어야 함.)

## 4.5. SpotBugs 오탐 처리 (국소 예외 — 정책 아님)

`ProviderResource.kind`/`provider` 는 public 필드인데, 값을 채우는 하위 클래스는 별도
프로바이더 레포에 있고 읽는 엔진은 리플렉션을 쓴다 → 이 모듈 정적 분석엔 "안 쓰이는 필드"로
보여 SpotBugs `UUF_UNUSED_PUBLIC_OR_PROTECTED_FIELD` 오탐이 난다. 라이브러리 공개 필드라 정상.

**결정: `@SuppressFBWarnings` 로 해당 필드에만 국소 억제** (+ `spotbugs-annotations` compileOnly 의존성).
프로젝트 전역 규칙(예: api/spi 공개 필드 계열 exclude)은 **아직 성급하다** — 데이터 포인트가
이거 하나뿐. `ResourceState` 등 상태 클래스에서 같은 패턴이 반복되는 게 확인되면 그때
"라이브러리 공개 필드 정책"을 재검토한다. (기록만 남기고 결정 보류)

## 5. 이번 범위 밖 (하지 않는 것)

- `ResourceScanner`, `Applier`, `Validator` 등 spi 모듈 인터페이스 구현.
- 실제 프로바이더 구현체(Aws, AwsKind, AwsEc2 등) — 이건 프로바이더 레포/브랜치 몫.
- 상태/변경 관련 타입(ScannedResources 등).

## 6. 검증 관점 (spec 에서 테스트로)

- `Kind`: 테스트용 enum 이 `implements Kind` 하고 `value()` 가 동작.
- `Provider`: 하위 클래스가 상속 가능(존재/확장).
- `ProviderResource`: 하위가 `kind`/`provider` 를 세팅·조회 가능.
- 어노테이션 3종: RUNTIME 유지 + 올바른 `@Target` + 속성값 왕복(리플렉션).
