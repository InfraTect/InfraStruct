# plan: plan-creator

## 목표

`Comparator`의 `ResourceChangeSet`을 두 가지 결과로 변환한다.

1. `Applier`가 실행할 `OrderedResourceChangeSet`
2. 사용자가 검토할 결정적인 텍스트 계획

## API와 책임

```java
public final class PlanCreator {
    public OrderedResourceChangeSet createPlan(ResourceChangeSet changeSet);
    public String renderExternalPlan(ResourceChangeSet changeSet);
}
```

- `PlanCreator`는 입력과 결과를 필드에 저장하지 않는다.
- `createPlan()`은 정렬된 계획을 직접 반환한다. getter는 두지 않는다.
- `renderExternalPlan()`은 선행 호출 없이 전달받은 변경 목록을 직접 포맷한다.
- 두 반환값을 소비하는 주체는 `InfraStruct`다.

## 내부 계획

- CREATE/UPDATE: `after().dependencies()`를 기준으로 의존 자원을 먼저 배치한다.
- DELETE: `before().dependencies()`를 기준으로 의존하는 자원을 먼저 배치한다.
- 전체 순서는 CREATE/UPDATE 묶음 후 DELETE 묶음이다.
- Kahn 위상 정렬과 logicalId 우선순위 큐로 결정성을 보장한다.
- 계획에 없는 의존성은 이미 존재하는 자원으로 보고 정렬에서 제외한다.

## 실패 계약

| 입력 | 처리 |
|---|---|
| `null` | `NullPointerException` |
| 중복 logicalId | `IllegalArgumentException` |
| CREATE/UPDATE 또는 DELETE 순환 | `IllegalArgumentException` |

실패하면 부분 계획을 반환하지 않는다. 예외는 `InfraStruct`로 전파하며,
`InfraStruct`는 이번 `Applier.apply()`를 중단해야 한다.

## 외부 계획

- CREATE → UPDATE → DELETE 순으로 묶고, 각 묶음은 logicalId 사전순으로 출력한다.
- CREATE는 `after().config()` 전체, UPDATE는 필드·의존성 diff, DELETE는 헤더만 출력한다.
- 필드와 의존성 diff는 필드명 사전순으로 출력한다.
- 목록값은 원소별로 따옴표를 붙이고, 줄바꿈은 `\n`으로 고정한다.
- 변경이 없으면 `No changes. Infrastructure is up-to-date.`를 반환한다.

## 범위 밖

- `Comparator`, `ResourceChange`, `ResourceChangeSet` 구조 변경
- `InfraStruct` 파이프라인과 `Applier` 구현
- CREATE/UPDATE와 DELETE를 하나로 합친 replacement 그래프
- 병렬 실행과 CLI 출력 정책
