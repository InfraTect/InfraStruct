# spec: plan-creator

## 공개 계약

```java
package com.infrastruct.internal;

public final class PlanCreator {
    public OrderedResourceChangeSet createPlan(ResourceChangeSet changeSet);
    public String renderExternalPlan(ResourceChangeSet changeSet);
}
```

- `ResourceChangeSet`은 `internal`, `OrderedResourceChangeSet`은 `spi`를 유지한다.
- 호출별 입력·결과를 필드에 저장하지 않는다.
- `createPlan()`과 `renderExternalPlan()`은 서로의 호출 여부에 의존하지 않는다.

## 테스트할 행동

### 내부 계획

- 빈 변경 목록은 빈 `OrderedResourceChangeSet`을 만든다.
- CREATE/UPDATE는 `after()`의 의존 자원을 먼저 배치한다.
- DELETE는 `before()`의 의존하는 자원을 먼저 배치한다.
- CREATE/UPDATE를 DELETE보다 먼저 배치한다.
- 실행 가능한 자원은 입력 순서와 무관하게 logicalId 사전순으로 고른다.
- 정렬 도중 새로 ready가 된 자원도 기존 ready 자원과 사전순으로 경쟁한다.
- 계획 밖의 의존성은 정렬을 막지 않는다.

### 실패와 무상태성

- `null`은 `changeSet`을 메시지에 포함한 `NullPointerException`으로 거부한다.
- 중복 logicalId는 logicalId를 메시지에 포함한 `IllegalArgumentException`으로 거부한다.
- upsert와 delete의 순환 의존성을 각각 `IllegalArgumentException`으로 거부한다.
- 실패 시 부분 계획을 반환하지 않는다.
- 연속 호출과 실패 후 정상 호출은 이전 입력·결과의 영향을 받지 않는다.

### 외부 계획

- `createPlan()` 없이도 독립적으로 렌더링한다.
- CREATE → UPDATE → DELETE, 그룹 내 logicalId 사전순을 유지한다.
- CREATE는 전체 config, UPDATE는 이전값→새값, DELETE는 헤더만 출력한다.
- 필드·의존성 diff를 필드명 사전순으로 출력한다.
- `Iterable` 원소별로 따옴표를 붙이고 모든 줄바꿈을 `\n`으로 출력한다.
- 빈 계획 문구와 create/update/delete 개수 요약을 검증한다.

## 검증 방법

- 순서는 AssertJ `containsExactly`, 전체 출력은 text block과 `isEqualTo`로 검증한다.
- upsert/delete 순환을 별도로 테스트한다.
- 전역 ready 사전순은 `alpha → bravo`와 독립 `charlie`의 `alpha, bravo, charlie`로
  검증한다.
- `ResourceChange`의 기존 불변 조건과 `Comparator` 테스트는 변경하지 않는다.
