# feature: current-state-store

브랜치: `feat/CurrentStateStore-impl`

## 1. 목표 (무엇을)

**`CurrentStateStore` 클래스 — 정의(뼈대)만.**

마지막으로 apply 된 실제 상태({@link CurrentResources})를 JSON 으로 저장/복원하는 내부 모듈이다.
하지만 실제 직렬화·역직렬화 로직은 아직 미해결 설계(§4)가 걸려 있어 이번엔 채우지 않는다.

이번 feature 의 목적은 **다른 사람이 이 타입을 import 해서 호출부를 먼저 엮을 수 있게** 하는 것이다.
따라서 **클래스와 공개 시그니처만** 확정하고 본문은 비운다(반환값이 있으면 빈 객체를 돌려준다).

> `InfraStruct` 뼈대(feature: infrastructapplication-annotation)와 같은 성격의 작업이다 —
> 공개 시그니처를 못 박아 뒤 단계를 언블록하고, 채워질 자리는 주석으로 남긴다.

## 2. 위치 — `com.infrastruct.internal` (왜 spi 가 아닌가)

`Comparator` 와 같은 `internal` 패키지에 둔다.

- `internal/package-info` 규칙: "엔진 내부 구현. 외부 노출 금지. 사용자·프로바이더 누구도
  여기에 의존하지 않는다 → 언제든 리팩터링 가능."
- `CurrentStateStore` 는 엔진이 상태 파일을 읽고 쓰는 내부 모듈일 뿐, 프로바이더가 구현하거나
  사용자가 손에 쥐는 타입이 아니다. 상태를 주고받는 **그릇**(`CurrentResources` 등)만 `spi` 에 있고,
  그 그릇을 다루는 **모듈**은 `internal` 에 있다(Comparator 가 선례).

## 3. 공개 시그니처 (무엇을)

```java
public final class CurrentStateStore {
    public CurrentResources load();               // 저장된 상태 파일 → CurrentResources
    public void save(CurrentResources resources); // CurrentResources → 상태 파일
}
```

파이프라인에서의 자리(`InfraStruct.run()` 흐름): compare 직전에 `load()` 로 이전 상태를 읽고,
apply 가 끝난 뒤 Applier 가 새로 만들어 준 `CurrentResources` 를 `save()` 로 기록한다.

이번 범위에서 **하는 것**:

- `com.infrastruct.internal.CurrentStateStore` 클래스 정의(`Comparator` 처럼 `final`).
- 위 공개 시그니처 **2개** 선언.
- 본문은 비운다. `load()` 는 최초 실행(파일 없음)과 같은 의미로 **빈 `CurrentResources`**
  (`new CurrentResources(List.of())`) 를 돌려주고, `save()` 는 아무것도 하지 않는다.
  대신 "원래 여기에 무엇이 들어갈지"를 주석으로 남긴다(throw 하지 않는다).

## 4. 왜 본문을 비우는가 — 직렬화 설계가 아직 미해결

`resource-state-classes` summary §7 이 `CurrentStateStore` 를 맡는 쪽에 넘긴 숙제가 그대로 남아 있다:

- **`kind` 는 인터페이스(`Kind`)** 라 Gson 직렬화는 되지만 역직렬화가
  `Interfaces can't be instantiated!` 로 깨진다 → 읽는 쪽에서 `TypeAdapter<Kind>` 로 풀어야 한다
  (providerId → 그 프로바이더의 Kind enum → `value()` 매칭).
- **상태 클래스가 불변**이라 인자 없는 생성자가 없다. Gson 이 `Unsafe` 로 인스턴스를 만들고
  `final` 필드를 리플렉션으로 채우면서 생성자의 `copyOf` 정규화를 건너뛴다 →
  `"config": null` 이 그대로 새는 것을 막으려면 `InstanceCreator` 등록 또는 읽은 뒤 정규화가 필요하다.

이 둘은 단순 코딩이 아니라 **설계 판단**이고, 이번 스켈레톤의 범위를 넘는다. 그래서 지금은
시그니처만 못 박아 호출부(InfraStruct 파이프라인, Applier)를 언블록하고, 실제 Gson 로직은
다음 feature 에서 채운다. 채워질 자리는 클래스/메서드 주석으로 표시한다.

이번 범위에서 **하지 않는 것 (그리고 왜)**:

- 실제 JSON 직렬화/역직렬화 → §4 의 설계(TypeAdapter/정규화)가 서야 채운다.
- 상태 파일 경로 결정·파일 IO → 위와 함께 다음 feature 에서.
- 생성자에 provider/경로 주입 → 지금 넣으면 `TypeAdapter<Kind>` 설계를 앞질러 잘못된 가정을
  강제한다. `InfraStruct` 가 필드 주입을 미룬 것과 같은 이유로 미룬다.

## 5. 검증 관점 (spec 단계에서 테스트로 바뀔 것들)

스텁이라 행위 테스트가 약하다. 최소한 다룰 수 있는 것:

- `CurrentStateStore` 클래스가 존재하고 공개 시그니처(`load` / `save`)를 가진다.
- `load()` 가 `null` 이 아니라 **빈 `CurrentResources`** 를 돌려준다(최초 실행 계약의 자리표시).
- `save(...)` 가 (스텁이므로) 예외 없이 호출된다.

실제 저장/복원 왕복(save→load 라운드트립) 검증은 §4 가 구현되는 다음 feature 의 몫이다.
(spec 단계에서 조율)
