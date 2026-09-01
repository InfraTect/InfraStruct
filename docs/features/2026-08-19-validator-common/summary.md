# summary: validator-common

## 완료

- `Violation`과 `ValidationResult`를 추가했다. 검증 오류는 예외 대신 불변 Violation 목록으로
  반환하고, 한 자원에 여러 오류를 담을 수 있다.
- `Validator.validate()`는 common과 provider 검증을 모두 실행해 결과를 병합한다.
- common 검증은 kind, logicalId, logicalId 중복, 필수 필드 이름, DFS 기반 dependency 순환을
  검사한다.
- cycle의 대상이 아직 DesiredResources에 없으면 존재 여부 검증이 확정될 때까지 건너뛴다.

## 보류

- dependency 대상 존재·중복 검사
- 스칼라·참조형 `@Required` 필드의 실제 충족 여부
- Provider별 설정값 및 자원 호환성 검증
- 구체적인 violation code 목록

## 검증

JDK 21로 `./gradlew build`를 실행해 테스트, Spotless, Checkstyle, SpotBugs를 통과했다.
