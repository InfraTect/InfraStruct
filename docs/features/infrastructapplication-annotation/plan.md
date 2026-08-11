# feature: infrastructapplication-annotation

브랜치: `feat/InfraStructApplication-annotation-impl`

## 1. 목표 (무엇을)

이 feature 에서 만드는 것은 **둘**이다.

1. **`@InfraStructApplication` 어노테이션 — 완전 구현.**
   사용자의 메인 클래스에 붙여, 이 애플리케이션이 어떤 프로바이더를 쓰는지 선언한다.
2. **`InfraStruct` 클래스 — 정의(뼈대)만.**
   프레임워크의 진입 클래스. 하지만 내부에서 쓰는 모듈들(ResourceScanner,
   DesiredStateCreator, Validator, CurrentStateStore, Comparator, PlanCreator,
   Applier)은 **아직 다른 브랜치/조원이 구현 중**이라 지금은 참조할 수 없다.
   따라서 이번엔 **클래스와 공개 시그니처만** 만들고 본문/의존 필드는 비운다.

## 2. 왜 provider 는 `String` 인가 (설계 근거)

`docs/plan.md` §22·§26·§144·§187 에 근거가 있다.

- 프로그래밍 모델: `@InfraStructApplication(provider="aws")`.
- provider 는 원래 enum 이었으나 **의도적으로 String 으로 바꿨다.**
  이유: 코어(framework)가 특정 클라우드 목록을 알면 안 된다. String 으로 두면
  코어에서 프로바이더 목록이 사라져 **코어가 클라우드를 완전히 모르게** 된다.
  (Terraform 코어가 aws 를 모르는 것과 같은 구조.)
- 런타임에 이 문자열(`"aws"`)로 `java.util.ServiceLoader` 가 실제 프로바이더를 찾는다.

> 다이어그램의 `provider(): String` 이 맞고, `@InfraStructApplication(Provider)`
> 라벨은 축약 표기였다.

## 3. `@InfraStructApplication` 상세 (무엇을/왜)

| 항목 | 값 | 왜 |
|---|---|---|
| 속성 | `String provider()` | 위 §2. |
| `@Retention` | `RUNTIME` | 컴파일 후 리플렉션으로 읽어야 하기 때문. `SOURCE`/`CLASS` 면 런타임에 사라진다. |
| `@Target` | `TYPE` | 사용자의 메인 **클래스**에 붙는다. |
| 패키지 | `com.infrastruct.api` | 사용자가 직접 import 해서 쓰는 공개 API. (package-info 예시에도 명시됨) |

기본값(default) 은 두지 않는다 — provider 선언은 필수다.

## 4. `InfraStruct` 상세 (무엇을/왜)

다이어그램상의 최종 모습(참고용, 이번엔 전부 구현하지 않음):

- 필드: `resourceScanner, desiredStateCreator, validator, currentStateStore,
  comparator, planCreator, applier`
- 메서드: `static void run(Class<?> mainClass)`, `InfraStruct(String provider)` 생성자,
  `void run()`

이번 범위에서 **하는 것**:

- `com.infrastruct.api.InfraStruct` 클래스 정의.
- 공개 시그니처 **3개**(위)를 선언한다.
- 각 메서드/생성자의 **본문은 비워둔다.** 대신 "원래 여기에 무엇이 들어갈지"를
  **주석으로 꼼꼼히** 남긴다 (throw 하지 않는다).

### 왜 본문을 비우는가 — 의존 주입 대상이 아직 없다

`new InfraStruct("aws")` 의 **원래 의도**는:

- `ModuleRegistry` 가 provider 문자열(`"aws"`)로 해당 프로바이더의 `Validator` /
  `Applier`(및 나머지 모듈)를 찾아 **InfraStruct 의 필드에 주입**하는 것.

그런데 `ModuleRegistry` 도, 주입 대상 모듈 타입들도 **아직 없다**(다른 브랜치).
따라서 생성자·`run()` 안에 넣을 로직의 재료가 없다 → 본문은 비우고, 채워질 자리를
주석으로 표시해 다음 사람이 이어받을 수 있게 한다.

이번 범위에서 **하지 않는 것 (그리고 왜)**:

- 모듈 필드 7개 → 타입들이 아직 없다(다른 브랜치). 지금 넣으면 컴파일이 깨진다.
- 생성자의 ModuleRegistry 조회·필드 주입 → ModuleRegistry 가 생긴 뒤 채운다.
- `run(Class<?>)` / `run()` 의 실제 로직 → 스캔·비교·플랜·적용 파이프라인은
  의존 모듈이 생긴 뒤에 다른 feature 에서 채운다.

## 5. 실행 흐름 (맥락)

`@InfraStructApplication(provider="aws")` 가 붙은 메인 → `InfraStruct.run(Main.class)`
→ 어노테이션에서 provider 문자열을 읽어 `new InfraStruct("aws")` → `run()`.

이번 feature 는 이 흐름의 **입구(어노테이션 정의 + 진입 클래스 뼈대)**까지만 만든다.
`run(Class<?>)` 안의 "어노테이션 읽기 → provider 문자열 추출 → `new InfraStruct(...)`
→ `run()`" 연결도 **주석으로만** 남기고 본문은 비운다(§4 참조).

## 6. 검증 관점 (spec 단계에서 테스트로 바뀔 것들)

어노테이션은 값이 있으므로 테스트할 수 있다. 예상 검증거리:

- `@InfraStructApplication` 이 `RUNTIME` 까지 살아남아 리플렉션으로 읽힌다.
- `TYPE` 에만 붙는다.
- 클래스에 붙인 뒤 `provider()` 값이 선언한 문자열과 같다.

`InfraStruct` 는 스텁이라 행위 테스트가 어렵다 — 최소한 "클래스가 존재하고
공개 시그니처가 있다" 수준까지만 다룬다. (spec 단계에서 조율)
