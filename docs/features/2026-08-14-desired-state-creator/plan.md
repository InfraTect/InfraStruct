# feature: desired-state-creator

브랜치: `feat/DesiredStateCreator-impl`

## 1. 목표 (무엇을)

**`DesiredStateCreator` 클래스 — 정의(뼈대)만.**

`ScannedResources`(스캐너 결과)를 받아, 각 자원에 붙어 있던 매크로 어노테이션
(`capturedAnnotations`)을 `BehaviorHandler.handle` 로 소비해 `config` 에 반영한 뒤
`DesiredResources`(Validator 의 검증 대상)를 만드는 내부 모듈이다.

하지만 실제 변환 로직은 아직 미해결 설계(§4)가 걸려 있어 이번엔 채우지 않는다. 이번 목적은
**다른 사람이 이 타입을 import 해서 호출부(InfraStruct 파이프라인, Validator)를 먼저 엮을 수
있게** 하는 것이다. 따라서 **클래스와 공개 시그니처만** 확정하고 본문은 비운다(반환값은 빈 객체).

> `InfraStruct` · `CurrentStateStore` 뼈대와 같은 성격의 작업이다.

## 2. 위치 — `com.infrastruct.internal` (왜 spi 가 아닌가)

`Comparator` · `CurrentStateStore` 와 같은 `internal` 패키지에 둔다.

- 상태를 주고받는 **그릇**(`ScannedResources` / `DesiredResources` 등)만 `spi` 에 있고,
  그 그릇을 다루는 **엔진 모듈**은 `internal` 에 둔다(`Comparator` 가 선례).
- `DesiredStateCreator` 는 엔진이 스캔 결과를 원하는 상태로 변환하는 내부 모듈일 뿐,
  프로바이더가 구현하거나 사용자가 손에 쥐는 타입이 아니다.

## 3. 공개 시그니처 (무엇을)

```java
public final class DesiredStateCreator {
    public DesiredResources create(ScannedResources scanned);  // 스캔 결과 → 원하는 상태
}
```

파이프라인에서의 자리(`InfraStruct.run()` 흐름): scan 직후 `create(scanned)` 로 원하는 상태를
만들고, 그 결과가 `Validator.validate(DesiredResources)` 의 입력이 된다.

이번 범위에서 **하는 것**:

- `com.infrastruct.internal.DesiredStateCreator` 클래스 정의(`Comparator` 처럼 `final`).
- 위 공개 시그니처 **1개** 선언.
- 본문은 비운다. `create()` 는 **빈 `DesiredResources`**(`new DesiredResources(List.of())`)
  를 돌려준다. 대신 "원래 여기에 무엇이 들어갈지"를 주석으로 남긴다(throw 하지 않는다).

## 4. 왜 본문을 비우는가 — 변환 로직의 전제가 아직 없다

`resource-state-classes` summary §87 이 `DesiredStateCreator` 를 맡는 쪽에 넘긴 숙제가 그대로 남아 있다:

1. **`BehaviorHandler.handle` 시그니처가 아직 임시다.** 지금은 `void handle(T, Object)` 이고,
   `state` 가 `Object` 자리표시자다. 상태 클래스가 **불변**이라 타입만 좁혀선 동작하지 않는다 —
   핸들러가 넘겨받은 상태를 고칠 수 없기 때문. 반환형까지 함께 정해야 한다(후보:
   `ScannedResourceState handle(T annotation, ScannedResourceState state)`). 이 확정은 실제
   호출부를 써 보며 편한지 확인한 뒤 하는 **설계 판단**이다.
2. **핸들러 조회(handlerFor)** — 어떤 `CapturedAnnotation` 을 어느 `BehaviorHandler` 로 보낼지
   찾아 주는 레지스트리가 필요한데, 그 주입 경로가 아직 없다(다른 브랜치의 ModuleRegistry 계열).

이 둘이 서기 전에 시그니처만 못 박아 호출부를 언블록하고, 실제 어노테이션 소비 로직은 다음
feature 에서 채운다. 채울 자리는 `create()` 의 `// TODO` 주석으로 표시한다.

이번 범위에서 **하지 않는 것 (그리고 왜)**:

- `capturedAnnotations` 소비 → `config` 반영 변환 → §4-1 의 `handle` 시그니처가 서야 채운다.
- `BehaviorHandler.handle(T, Object)` 자리표시자 좁히기 → **남의 파일**이고, 반환형까지 얽힌
  설계 판단이라 실제 호출부와 함께 별도로 정한다(summary §2 의 규율: 남의 파일 안 건드림).
- 생성자에 핸들러 레지스트리 주입 → 그 주입 경로(ModuleRegistry)가 생긴 뒤 채운다. 지금은
  인자 없는 생성자.

## 5. 검증 관점 (spec 단계에서 테스트로 바뀔 것들)

스텁이라 행위 테스트가 약하다. 최소한 다룰 수 있는 것:

- `DesiredStateCreator` 클래스가 존재하고 공개 시그니처(`create`)를 가진다.
- `create(scanned)` 가 `null` 이 아니라 **빈 `DesiredResources`** 를 돌려준다(변환 로직의 자리표시).

스캔 결과를 실제로 원하는 상태로 바꾸는 검증(어노테이션 소비 → config 반영)은 §4 가 구현되는
다음 feature 의 몫이다. (spec 단계에서 조율)
