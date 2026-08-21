# summary: plan-creator

## 구현 결과

`ResourceChangeSet`을 실행용 계획과 사용자용 텍스트로 변환하는 무상태
`PlanCreator`를 구현했다.

```java
OrderedResourceChangeSet createPlan(ResourceChangeSet changeSet);
String renderExternalPlan(ResourceChangeSet changeSet);
```

- CREATE/UPDATE는 의존 자원 먼저, DELETE는 의존하는 자원 먼저 정렬한다.
- logicalId 우선순위 큐로 결과의 결정성을 보장한다.
- 중복 logicalId와 순환 의존성은 `IllegalArgumentException`, `null`은
  `NullPointerException`으로 거부한다.
- 부분 계획은 반환하지 않고, 호출 상태도 저장하지 않는다.

`PlanCreatorTest` 18개와 `./gradlew :framework:check`가 모두 통과했다.

## InfraStruct 개발자에게

`PlanCreator`의 **두 반환값을 소비하는 주체는 `InfraStruct`**다.

```java
ResourceChangeSet changes = comparator.compare(desired, current);

OrderedResourceChangeSet internalPlan = planCreator.createPlan(changes);
String externalPlan = planCreator.renderExternalPlan(changes);

// InfraStruct가 externalPlan을 출력한다.
CurrentResources updated = applier.apply(internalPlan, current);
currentStateStore.save(updated);
```

`PlanCreator`는 직접 출력·apply하지 않는다. `InfraStruct`가 문자열을 출력하고
`OrderedResourceChangeSet`을 `Applier`에 전달해야 한다.

### 예외 처리 규칙

1. `createPlan()`이 실패하면 `Applier.apply()`와 `CurrentStateStore.save()`를 호출하지
   말라.
2. 예외를 빈 계획, `false`, `null`로 바꾸지 말라. “변경 없음”과 “계획 실패”가
   구분되어야 한다.
3. 정렬된 일부 자원만 apply하지 말라.
4. `IllegalArgumentException`은 실행을 중단하고 원인을 사용자에게 알리거나 상위로
   재전파하라.
5. `NullPointerException`은 일반 입력 오류가 아니라 파이프라인 구성 버그로 다루라.

## 남은 일

- `InfraStruct.run()`에 두 반환값 소비와 예외 처리를 연결한다.
- 계획 실패 시 `Applier`와 상태 저장소가 호출되지 않는지 테스트한다.
