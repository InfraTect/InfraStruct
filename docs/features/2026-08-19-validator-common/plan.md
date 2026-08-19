# plan: validator-common

브랜치: `feat/validator-common`

## 1. 목표 — 무엇을 만드나

`Validator.validateCommon(DesiredResources)` 에 모든 프로바이더가 공통으로 지켜야 하는
**Desired State의 구조적 검증과 dependency 순환 검증**을 구현한다.

파이프라인에서 `DesiredResources` 는 `DesiredStateCreator` 가 매크로 어노테이션을 모두 소비한
뒤 만든 불변 스냅샷이다. `validateCommon` 은 이 값을 수정하지 않고 읽어서, Comparator와
프로바이더별 Validator가 안전하게 사용할 수 있는 기본 조건을 보장한다.

```text
Scanner
  → DesiredStateCreator(매크로 검증·소비)
  → DesiredResources
  → Validator.validateCommon(공통 구조 검증)
  → Validator.validateProviderResource(프로바이더 규칙 검증)
  → Comparator
```

## 2. 현재 모델에서 검증할 데이터

`DesiredResources` 는 `List<DesiredResourceState>` 를 담고, 각 상태는 부모인
`ResourceState` 로부터 다음 값을 갖는다.

- `kind`: 프로바이더가 정의한 자원 종류.
- `logicalId`: Comparator가 Desired와 Current를 연결하는 전체 자원 식별자.
- `config`: 매크로 적용까지 끝난 스칼라 설정값.
- `dependencies`: 참조 대상의 logicalId 목록. 참조 필드명은 아직 보존하지 않는다.
- `requiredFields`: Scanner가 `@Required` 에서 수집한 필드 이름 집합.

`DesiredResources` 와 `ResourceState` 생성자는 컬렉션을 `List.copyOf` / `Map.copyOf` /
`Set.copyOf` 로 복사한다. 따라서 null 컬렉션·null 원소·null config key/value는 생성 시점에 이미
거부된다. 반면 `kind`, `kind.value()`, `logicalId`의 null·공백과 logicalId 중복은 생성자가
막지 않으므로 Validator가 검사한다.

## 3. 이번 feature의 공통 검증 규칙

### 3.1 전체 입력

- `validateCommon(null)` 을 허용하지 않는다.
- null 입력은 프로그래밍 오류이므로 `NullPointerException`을 던지고 메시지에
  `desiredResources`를 포함한다.
- 빈 `DesiredResources` 는 유효하다. 선언할 자원이 없는 상태 자체는 구조 오류가 아니다.

### 3.2 Kind

각 자원은 다음 조건을 모두 만족해야 한다.

- `kind`가 null이 아니다.
- `kind.value()`가 null이 아니다.
- `kind.value()`가 빈 문자열 또는 공백 문자열이 아니다.

Kind별 허용 설정값, 자원 간 Kind 호환성은 공통 규칙이 아니므로 Provider Validator가 맡는다.

### 3.3 logicalId

각 자원의 `logicalId`는 null·빈 문자열·공백 문자열이면 안 된다. 전체 `DesiredResources`
안에서 logicalId는 Kind와 무관하게 고유해야 한다.

Comparator가 `(kind, logicalId)`가 아니라 logicalId 하나만으로 자원을 인덱싱하므로 다음 두
자원은 중복이다.

```text
kind=VPC,    logicalId=shared
kind=SUBNET, logicalId=shared
```

logicalId의 문자 집합이나 `kind.name` 형식은 아직 프로젝트 계약으로 확정되지 않았으므로 이번
feature에서 정규식을 도입하지 않는다.

### 3.4 필수 필드

`requiredFields`의 필드 이름은 null·빈 문자열·공백 문자열이면 안 된다. null 원소는
`Set.copyOf`가 이미 차단하므로 Validator에서는 빈 문자열과 공백 문자열을 검사한다.

`@Required`는 스칼라 필드뿐 아니라 `@Required Vpc vpc` 같은 참조 필드에도 붙을 수 있다.
그러나 현재 모델은 `requiredFields`의 각 이름이 스칼라인지 참조인지 구분하지 않고, 참조 필드와
대상 logicalId도 연결하지 못한다.

```text
requiredFields = [vpc]
dependencies   = [vpc.main]
```

`dependencies`에 참조 필드명 `vpc`가 없기 때문에 Validator는 `vpc` 필드가 `vpc.main`으로
채워졌는지 알 수 없다. 따라서 참조형 필수 필드를 `config.containsKey()`만으로 검사하면 정상
입력을 누락으로 오판한다.

반대로 `requiredFields=[cidrBlock]`, `config={}`만 보고 `cidrBlock`이 스칼라 필드였다고 판단할
근거도 Desired State 안에는 없다. 이름만으로 필드 종류를 추측하면 안 된다. 따라서 이번
feature에서는 **필수 필드 이름의 구조만 검사하고, 실제 충족 여부는 검사하지 않는다.**

후속 dependency feature에서는 최소한 다음과 같이 참조 필드명을 보존해야 한다.

```java
public record ResourceDependency(String fieldName, String targetLogicalId) {}
```

그 모델과 필드 분류 방식이 확정되면 필수 필드 존재 조건을 `config key 또는 dependency
fieldName`으로 완성한다. config 값의 null은 `Map.copyOf(config)`가 상태 생성 시점에 이미
거부한다.

### 3.5 dependency 순환

자원 종류별로 어떤 참조 필드를 허용하는지는 아직 조사 중이지만, 현재
`List<String> dependencies`만으로도 logicalId 사이의 방향 그래프는 만들 수 있다.

```text
subnet.public → vpc.main
ec2.web       → subnet.public
```

각 자원을 정점, `resource.dependencies()`의 target logicalId를 방향 간선으로 보고 DFS로
순환을 검사한다. 자기 자신을 참조하는 경우도 길이 1인 순환으로 처리한다.

```text
a → a                 // 자기 순환
a → b → c → a         // 간접 순환
a → b, a → c, b → d   // 순환 아님
```

이번 feature는 dependency **대상 존재 여부를 판정하지 않는다.** target logicalId가 현재
`DesiredResources`의 logicalId 인덱스에 없으면 DFS에서 그 간선을 따라가지 않고 건너뛴다. 대상
존재 검증은 자원 의존성 자료 조사가 끝난 뒤 별도 규칙으로 추가한다.

## 4. 반환 계약 — ValidationResult

검증 결과는 `ValidationResult`로 확정한다. 사용자 입력 오류는 예외로 즉시 중단하지 않고
`Violation`으로 수집한다. 한 자원에서 여러 문제가 발견되면 같은 logicalId를 가진 Violation을
여러 개 담는다.

```java
public record Violation(
        String code,
        String logicalId,
        String field,
        String message) {}
```

`code`는 프로그램이 위반 종류를 식별하는 안정적인 문자열이다. 구체적인 code 목록은 검증
규칙이 확정될 때 추가하고, Provider도 자신의 code를 정의할 수 있게 닫힌 공통 enum으로 제한하지
않는다. `logicalId`와 `field`는 해당 위치를 특정할 수 없는 전체 입력·그래프 오류에서 null일 수
있다.

```java
public record ValidationResult(List<Violation> violations) {

    public ValidationResult {
        violations = List.copyOf(violations);
    }

    public static ValidationResult valid() {
        return new ValidationResult(List.of());
    }

    public boolean isValid() {
        return violations.isEmpty();
    }

    public ValidationResult merge(ValidationResult other) {
        List<Violation> merged = new ArrayList<>(violations);
        merged.addAll(other.violations());
        return new ValidationResult(merged);
    }
}
```

성공 여부를 나타내는 boolean 필드는 따로 저장하지 않는다. `violations.isEmpty()`에서 계산해
boolean 값과 목록이 서로 모순되는 상태를 만들지 않는다. ValidationResult와 내부 목록은
불변이며, 정상 결과는 빈 목록을 가진다.

`desiredResources == null`은 검증할 사용자 선언이 아니라 API를 잘못 호출한 프로그래밍
오류이므로 `NullPointerException`을 던진다. 그 밖의 예상 가능한 사용자 입력 오류는
ValidationResult에 담는다. Validator는 결과를 직접 stderr에 출력하지 않는다.

## 5. 구현 구조와 검사 순서

```java
public final ValidationResult validate(DesiredResources desiredResources) {
    Objects.requireNonNull(desiredResources, "desiredResources");

    ValidationResult common = validateCommon(desiredResources);
    ValidationResult provider = validateProviderResource(desiredResources);
    return common.merge(provider);
}

protected final ValidationResult validateCommon(DesiredResources desiredResources) {
    Objects.requireNonNull(desiredResources, "desiredResources");
    List<Violation> violations = new ArrayList<>();

    validateResourceIdentities(desiredResources, violations);
    Map<String, DesiredResourceState> resourcesById =
            indexByLogicalId(desiredResources, violations);
    validateRequiredFieldNames(desiredResources, violations);
    validateNoDependencyCycles(resourcesById, violations);

    return new ValidationResult(violations);
}

protected ValidationResult validateProviderResource(
        DesiredResources desiredResources) {
    return ValidationResult.valid();
}
```

`validate()`는 common 결과가 invalid여도 provider 검증을 호출한다. 두 결과를 합쳐 사용자가 한
번의 실행으로 가능한 오류를 함께 확인할 수 있게 한다. Provider Validator는 common 검증이 먼저
성공했다고 가정하지 않고 자신이 해석할 수 없는 자원이나 필드는 건너뛰어야 한다.
기본 provider 구현은 위반 없는 결과를 반환하고, 실제 Provider가 이를 재정의한다.

검사 순서는 다음과 같다.

1. 전체 입력 null 검사.
2. 각 자원의 kind와 logicalId 기본 오류를 Violation으로 수집.
3. 유효한 logicalId로 인덱스를 만들며 중복 오류를 수집.
4. 필수 필드 이름 오류를 수집.
5. logicalId 인덱스를 기준으로 dependency 그래프의 순환 오류를 DFS로 수집.

앞 단계가 뒤 단계의 전제다. 예를 들어 logicalId가 유효해야 중복 오류 메시지와 향후 dependency
검색 인덱스를 안정적으로 만들 수 있다.

## 6. 테스트 전략

`validateCommon`은 `protected final`이므로 테스트에서 재정의하지 않는다. 최소 Validator 하위
타입에 단순 위임 메서드를 두어 공통 검증 결과만 노출한다.

```java
private static final class TestValidator extends Validator {
    ValidationResult validateOnlyCommon(DesiredResources desiredResources) {
        return validateCommon(desiredResources);
    }
}
```

테스트는 정상 입력, null 전체 입력, null·공백 kind 값, null·공백 logicalId, Kind가 다른 자원 간
logicalId 중복, 공백 필수 필드명, dependency의 자기 순환과 간접 순환을 각각 독립적으로
검증한다. 오류는 예외가 아니라 반환된 violations에서 logicalId·field·message를 확인한다. 여러
경로가 한 자원으로 합쳐지지만 순환하지 않는 그래프도 정상 입력으로 검증한다.

별도 테스트에서 한 자원에 두 오류를 만들어 같은 logicalId의 Violation이 둘 다 보존되는지,
common과 provider가 각각 오류를 반환할 때 `validate()`가 두 결과를 모두 합치는지 확인한다.

## 7. 이번 feature 범위 밖

- dependency 대상 존재 여부와 중복 참조 검사.
- 스칼라·참조형 `@Required` 필드의 실제 충족 여부 검사.
- 매크로 어노테이션 자체와 `BehaviorHandler` 실행 검증. 이는 DesiredStateCreator 단계의 책임이다.
- Kind별 config 타입·범위·조합과 자원 간 호환성. 이는 Provider Validator의 책임이다.
- 구체적인 공통·Provider violation code 전체 목록.
- `RegisterProvider.validator()`의 제네릭 상한 변경.

## 8. DFS 순환 검증 설계

DFS는 각 정점의 방문 상태를 세 가지로 구분한다.

```java
private enum VisitState {
    VISITING,
    VISITED
}
```

Map에 상태가 없으면 아직 방문하지 않은 정점이다. `VISITING`은 현재 DFS 호출 경로에 있는 정점,
`VISITED`는 해당 정점에서 출발하는 모든 경로의 검사가 끝난 정점이다.

```java
private static void validateNoDependencyCycles(
        Map<String, DesiredResourceState> resourcesById,
        List<Violation> violations) {
    Map<String, VisitState> states = new HashMap<>();
    Deque<String> path = new ArrayDeque<>();

    for (String logicalId : resourcesById.keySet()) {
        if (!states.containsKey(logicalId)) {
            visit(logicalId, resourcesById, states, path, violations);
        }
    }
}

private static void visit(
        String logicalId,
        Map<String, DesiredResourceState> resourcesById,
        Map<String, VisitState> states,
        Deque<String> path,
        List<Violation> violations) {
    states.put(logicalId, VisitState.VISITING);
    path.addLast(logicalId);

    for (String targetId : resourcesById.get(logicalId).dependencies()) {
        if (!resourcesById.containsKey(targetId)) {
            continue;
        }

        VisitState targetState = states.get(targetId);
        if (targetState == VisitState.VISITING) {
            violations.add(cyclicDependency(path, targetId));
            continue;
        }
        if (targetState == null) {
            visit(targetId, resourcesById, states, path, violations);
        }
    }

    path.removeLast();
    states.put(logicalId, VisitState.VISITED);
}
```

정점 방문 규칙은 다음과 같다.

1. 현재 정점을 `VISITING`으로 표시하고 DFS 경로 끝에 넣는다.
2. 현재 자원의 각 target logicalId를 순회한다.
3. target이 `resourcesById`에 없으면 후속 존재 검증의 몫이므로 건너뛴다.
4. target이 `VISITING`이면 현재 경로로 되돌아가는 간선이므로 순환 Violation을 추가한다.
5. target이 `VISITED`면 이미 안전하게 검사를 끝낸 정점이므로 건너뛴다.
6. 방문하지 않은 target이면 재귀적으로 방문한다.
7. 모든 간선을 확인하면 현재 정점을 경로에서 제거하고 `VISITED`로 표시한다.

모든 logicalId에서 DFS를 시작하므로 서로 연결되지 않은 그래프 컴포넌트에 있는 순환도 찾는다.
오류 메시지에는 가능한 경우 `a -> b -> c -> a` 형태의 순환 경로를 포함한다. 탐색 시간은
정점 수를 V, dependency 수를 E라고 할 때 `O(V + E)`이고 추가 공간은 `O(V)`다.

## 9. 후속 dependency 검증

참조 필드명을 보존하는 dependency 모델이 확정되면 다음 공통 규칙을 추가한다.

1. dependency의 fieldName과 targetLogicalId가 null·공백이 아닌지 확인한다.
2. targetLogicalId가 전체 DesiredResources에 존재하는지 확인한다.
3. 같은 참조 필드 또는 같은 대상이 잘못 중복되지 않는지 확인한다.
4. requiredFields의 각 이름이 config key 또는 dependency fieldName에 존재하는지 확인한다.

순환과 자기 참조는 이번 feature의 DFS가 먼저 보장하므로 후속 작업에서 중복 구현하지 않는다.

## 10. 산출물

- `framework/src/main/java/com/infrastruct/spi/Validator.java`: `validateCommon` 공통 검증 구현.
- `framework/src/main/java/com/infrastruct/spi/Violation.java`: 구조화된 위반 하나.
- `framework/src/main/java/com/infrastruct/spi/ValidationResult.java`: 불변 위반 목록과 결과 병합.
- `framework/src/test/java/com/infrastruct/spi/ValidatorTest.java`: 공통 검증 단위 테스트.
- `docs/features/2026-08-19-validator-common/plan.md`: 설계 결정과 범위.
- `docs/features/2026-08-19-validator-common/spec.md`: TDD 행동 명세.
