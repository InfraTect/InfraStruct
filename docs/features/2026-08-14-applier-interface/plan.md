# plan: applier-interface

브랜치: `feat/impl-applier-interface`

## 1. 목표 — 무엇을 만드나

프로바이더가 구현하는 **`Applier`** 인터페이스(spi) 하나를 확정한다. InfraStruct 파이프라인의
마지막 단계로, `PlanCreator` 가 만든 순서 있는 변경셋을 실제 클라우드에 **적용(apply)** 하고
그 결과 실제 상태를 새 `CurrentResources` 로 돌려주는 계약이다.

```
Scanner → DesiredStateCreator → Validator → Comparator → PlanCreator → [Applier]
```

> Terraform 비유: `terraform plan` 산출물(`OrderedResourceChangeSet`)을 받아
> `terraform apply` 하는 부분. 프로바이더(AWS 등) 작성자가 직접 `implements Applier` 하는
> **확장점**이다.

## 2. 인터페이스 시그니처 (확정)

```java
package com.infrastruct.spi;

public interface Applier {
    CurrentResources apply(OrderedResourceChangeSet plan, CurrentResources current);
}
```

- **입력 `plan`**: `PlanCreator` 가 의존성 순서로 정렬한 `OrderedResourceChangeSet`
  (CREATE/UPDATE/DELETE `ResourceChange` 목록).
- **입력 `current`**: 직전에 apply 된 실제 상태(`CurrentResources`).
- **반환 `CurrentResources`**: apply 후의 새 실제 상태 전체.

근거는 이미 코드/문서에 예약돼 있다:
- `OrderedResourceChangeSet.java`: "Applier가 이 순서대로 적용한다."
- `CurrentResources.java`: "Applier 가 적용 후 새로 만들어 반환한다." → 반환형 = `CurrentResources`.
- `CurrentResourceState.physicalId`: "Applier 가 자원을 만들기 전엔 알 수 없어 null" → apply 가
  physicalId 를 채운다.
- `resource-state-classes/plan.md` §5 표: `Applier.apply(…, CurrentResources)`.

## 3. 왜 `current` 도 받나 — 설계 근거

`plan` 에는 **바뀐 자원만** 들어 있다 (`ChangeType` 은 CREATE/UPDATE/DELETE 뿐, NO_CHANGE
없음). apply 후의 완전한 상태를 만들려면 **안 바뀐 자원**을 이어실어야 하고, 그건 직전
`current` 에서 온다. 그래서 `plan` 하나로는 부족하고 `current` 를 함께 받아
"변경 적용 + 무변경 이월"을 합친 새 `CurrentResources` 를 반환한다.

## 4. 패키지 배치 — `spi`

`CONTRIBUTING.md` 의 판별 규칙("엔진 밖의 누군가가 이 타입을 코드에서 직접 구현/사용하는가?")
으로 `spi` 다. 프로바이더 작성자가 `implements Applier` 한다.
- 대조: `Comparator` 는 엔진이 소유 → `internal`. `Applier`/`Validator` 는 프로바이더 구현 →
  `spi`. (이미 `resource-state-classes/plan.md` §5 가 그렇게 못박음.)

## 5. 파급 — `@RegisterProvider.applier()`

`RegisterProvider.applier()` 는 지금 `Class<?>` 자리표시자다("Applier 가 생기면
`Class<? extends Applier>` 로 좁힌다"는 TODO 주석 보유). Applier 가 생기므로 좁힌다.

**결정: 이번 feature 범위에 포함한다.** Applier 계약을 확정하는 김에 그 타입을 쓰는 예약
자리표시자도 함께 조인다.
- `RegisterProvider.java`: `Class<?> applier()` → `Class<? extends Applier>` + TODO 주석 제거.
- `RegisterProviderTest`: 픽스처 `DummyApplier {}` → `implements Applier` 하는 구현으로 교체
  (좁아진 상한을 실제로 만족하는지 테스트가 증명).
- 주의: `validator()` 는 이번 범위 아님 (`Validator` 타입이 아직 없음). `applier()` 만 좁힌다.

## 6. 범위 밖

- 실제 프로바이더의 `Applier` 구현체(AWS 등) — 사용례이지 계약이 아님.
- `Validator` 인터페이스 — 별개 작업.
- `InfraStruct` 파이프라인 배선(`apply` 호출부) — `ModuleRegistry` 가 아직 없음.
- apply 실패/부분 적용/롤백 시맨틱 — 초기 계약에선 다루지 않고, 실제 호출부와 프로바이더가
  생길 때 예외 정책으로 확장. (열어 둠)

## 7. 검증 방법

`BehaviorHandler` 와 동일 패턴: 테스트용 `Applier` 구현 픽스처를 두고, `apply(plan, current)`
호출이 계약대로 동작(넘긴 값 처리 + `CurrentResources` 반환)하는지 확인.

## 확정된 결정 (리뷰 반영)

1. §2 시그니처 — `CurrentResources apply(OrderedResourceChangeSet, CurrentResources)` 그대로 확정.
2. §5 `@RegisterProvider.applier()` 좁히기 — **이번 범위 포함** (`applier()` 만, `validator()` 제외).
3. §6 apply 실패/부분 적용 시맨틱 — 지금 계약에 넣지 않고 **열어 둔다.**

## 이 feature 의 산출물 (요약)

- `spi/Applier.java` — 신규 인터페이스.
- `spi/RegisterProvider.java` — `applier()` 상한 좁히기.
- 테스트: `Applier` 계약 검증 + `RegisterProvider` 의 좁아진 상한 검증.
