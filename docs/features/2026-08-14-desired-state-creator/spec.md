# spec: desired-state-creator

`plan.md` 를 테스트 가능한 **행동 목록**으로 옮긴 것. 아래 `- ` 불릿들이 첫 `/red`
때 체크리스트(`behaviors[]`)로 등록되고, 위에서부터 순서대로 red→green 을 돈다.

`DesiredStateCreator` 는 **뼈대만** 만드는 feature 다(근거: `plan.md` §1, §4). 따라서 행동은
"공개 시그니처가 존재하고, 스텁 계약(빈 값 반환)을 지킨다" 수준으로 좁다.

## 행동 목록 (red 사이클 순서)

- DesiredStateCreator 는 인자 없이 인스턴스를 만들 수 있다
- create(ScannedResources) 는 null 이 아니라 빈 DesiredResources 를 돌려준다

## 공개 인터페이스 시그니처 (확정)

### `com.infrastruct.internal.DesiredStateCreator`

```java
public final class DesiredStateCreator {

    public DesiredResources create(ScannedResources scanned) {
        // 본문 비움(스텁). 원래: scanned 의 각 자원에서 capturedAnnotations 를 BehaviorHandler.handle
        // 로 소비해 config 에 반영 → DesiredResourceState 로 변환 → DesiredResources 로 묶는다.
        // (handle 시그니처 확정 + 핸들러 레지스트리가 서야 채운다 — plan §4.)
        return new DesiredResources(List.of());
    }
}
```

## 검증 메모 (어떻게 테스트할지)

> 주의: 이 절은 **행동이 아니라 구현 힌트**다. 하네스가 `- ` 불릿을 행동으로 자동
> 등록하므로, 여기서는 일부러 `- ` 대신 번호 목록을 쓴다.

1. 인스턴스화: `new DesiredStateCreator()` 가 예외 없이 만들어진다.
2. create 계약: 비지 않은 `ScannedResources` 를 넘겨도 `create(...)` 가 `null` 이 아니고
   `resources()` 가 빈 리스트다(스텁 = 아직 아무 어노테이션도 소비하지 않음의 자리표시).
   실제 변환(어노테이션 소비 → config 반영) 검증은 plan §4 구현 이후 feature 의 몫.
