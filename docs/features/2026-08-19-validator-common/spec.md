# spec: validator-common

`plan.md`를 테스트 가능한 **행동 목록**으로 옮긴 것이다. 아래 `- ` 불릿이 첫 `/red` 때
체크리스트로 등록되고 위에서부터 red→green을 돈다.

> ⚠️ 하네스 주의: `resync_behaviors`는 spec.md의 **모든 `- ` 줄**을 행동으로 등록한다.
> 따라서 행동 목록 밖에서는 번호 목록을 사용한다.

## 행동 목록 (red 사이클 순서)

- ValidationResult는 위반이 없으면 valid이고 불변인 빈 목록을 가진다
- ValidationResult는 한 자원에서 발생한 여러 Violation을 모두 보존한다
- ValidationResult.merge는 두 결과의 Violation을 빠짐없이 합친다
- validateCommon은 유효한 DesiredResources에 빈 ValidationResult를 반환한다
- validateCommon은 null DesiredResources를 거부한다
- validateCommon은 kind가 없는 자원의 Violation을 반환한다
- validateCommon은 kind.value()가 null인 자원의 Violation을 반환한다
- validateCommon은 kind.value()가 공백인 자원의 Violation을 반환한다
- validateCommon은 logicalId가 null인 자원의 Violation을 반환한다
- validateCommon은 logicalId가 공백인 자원의 Violation을 반환한다
- validateCommon은 Kind가 달라도 중복된 logicalId의 Violation을 반환한다
- validateCommon은 공백인 필수 필드 이름의 Violation을 반환한다
- validateCommon은 순환이 없는 dependency 그래프를 받아들인다
- validateCommon은 자기 자신을 dependency로 참조하는 자원의 Violation을 반환한다
- validateCommon은 여러 자원을 거치는 dependency 순환의 Violation을 반환한다
- validate는 common과 provider 검증을 단축 평가하지 않고 두 결과를 합친다

## 검증 대상 시그니처

```java
public abstract class Validator {

    public final ValidationResult validate(DesiredResources desiredResources);

    protected final ValidationResult validateCommon(
            DesiredResources desiredResources);

    protected ValidationResult validateProviderResource(
            DesiredResources desiredResources) {
        return ValidationResult.valid();
    }
}
```

스켈레톤의 `Object` 반환형은 `ValidationResult`로 교체한다.

## 결과 계약

1. `desiredResources == null`은 `NullPointerException`이며 메시지에 `desiredResources`가
   포함된다.
2. 예상 가능한 사용자 입력 오류는 예외가 아니라 `ValidationResult.violations()`에 담긴다.
3. 위반이 없으면 `isValid()`는 true이고 violations는 빈 불변 목록이다.
4. 위반이 하나 이상이면 `isValid()`는 false다.
5. 한 자원에서 여러 위반이 발생하면 같은 logicalId의 Violation을 여러 개 담을 수 있다.
6. kind 오류의 Violation 메시지에는 `kind`가 포함된다.
7. logicalId 오류의 Violation에는 가능한 경우 실제 logicalId가 포함된다.
8. 공백 필수 필드명 Violation 메시지에는 `필수 필드`가 포함된다.
9. dependency 순환 Violation 메시지에는 순환에 포함된 logicalId가 들어간다.
10. Validator는 Violation을 직접 stderr에 출력하지 않는다.

## 테스트 픽스처

`validateCommon`은 `protected final`이므로 재정의하지 않고 테스트 하위 타입에서 호출만
노출한다.

```java
private static final class TestValidator extends Validator {

    ValidationResult validateOnlyCommon(DesiredResources desiredResources) {
        return validateCommon(desiredResources);
    }
}
```

각 테스트는 `DesiredResourceState`를 직접 구성한다. 이는 Validator가 실제 `@Required`
어노테이션을 리플렉션으로 읽는 모듈이 아니라, Scanner가 이미 수집한 `requiredFields`를 소비하는
모듈이기 때문이다.

## 검증 메모 (행동 아님)

1. 정상 입력은 `kind=test-kind`, `logicalId=vpc.main`,
   `config={cidrBlock=10.0.0.0/16}`, `requiredFields={cidrBlock}`인 한 자원으로 구성한다.
2. null kind와 `kind.value()`의 null·공백은 서로 다른 테스트로 둬 Violation 원인을 분리한다.
3. logicalId의 null·공백도 서로 다른 테스트로 둔다.
4. 중복 검증은 `kind=vpc`와 `kind=subnet`에 같은 logicalId `shared`를 사용한다. 이 테스트는
   logicalId 고유 범위가 Kind 내부가 아니라 DesiredResources 전체임을 증명한다.
5. 실제 필드의 `@Required` 발견과 `requiredFields` 전달은 ResourceScanner 및 변환 단계의 별도
   테스트 대상이다.
6. 비순환 그래프는 `a→b`, `a→c`, `b→d`, `c→d`처럼 경로가 합쳐지는 형태로 만들어 단순히
   같은 정점에 두 번 도달하는 것을 순환으로 오판하지 않는지 확인한다.
7. 자기 순환은 `a→a`로 검증한다. 별도 자기 참조 규칙이 아니라 DFS의 `VISITING` 정점 재진입으로
   발견되어야 한다.
8. 간접 순환은 `a→b→c→a`로 검증하고, Violation 메시지에 `a`, `b`, `c`가 포함되는지
   확인한다.
9. 순환이 시작 정점과 떨어진 컴포넌트에 있어도 찾도록 정상 컴포넌트와 순환 컴포넌트를 함께
   넣는 경우를 포함한다.
10. 현재 DesiredResources에 없는 target logicalId는 이번 순환 검사에서 건너뛴다. 존재 여부는
    후속 dependency 검증 행동으로 추가한다.

## 보류된 행동

1. 빈 DesiredResources 허용 여부는 plan에서 유효로 결정했지만 현재 단위 테스트에 별도 행동으로
   등록하지 않았다. 정상 입력 행동에 보강하거나 구현 시 독립 테스트를 추가할 수 있다.
2. dependency 대상 존재와 중복 참조는 dependency 모델 확정 후 행동으로 추가한다.
3. 필수 필드의 실제 충족 여부는 필드 종류를 보존하고 dependency가 `fieldName`을 보존한 뒤
   검증한다.
4. 매크로 어노테이션 검증은 DesiredStateCreator 또는 BehaviorHandler의 spec에서 다룬다.
5. Provider별 config와 Kind 호환성은 `validateProviderResource`의 spec에서 다룬다.
6. 구체적인 violation code 목록은 각 검증 규칙을 추가할 때 확정한다.
