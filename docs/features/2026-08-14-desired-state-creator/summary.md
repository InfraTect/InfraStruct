# summary: desired-state-creator

## 무엇을 만들었나

`com.infrastruct.internal.DesiredStateCreator` — 스캔 결과를 사용자가 원하는 최종 상태로
변환하는 내부 모듈의 **뼈대(스텁)**. 공개 시그니처만 확정하고 본문은 비웠다.

| 메서드 | 계약 | 현재 동작(스텁) |
|---|---|---|
| `DesiredResources create(ScannedResources)` | 각 자원의 `capturedAnnotations` 를 `BehaviorHandler.handle` 로 소비해 `config` 에 반영 → `DesiredResources` | 항상 빈 `DesiredResources` 반환 |

파이프라인에서의 자리(`InfraStruct.run()`): scan 직후 `create()`, 결과는
`Validator.validate(DesiredResources)` 의 입력.

테스트 2개 추가(`DesiredStateCreatorTest`), 전체 `./gradlew check` 통과.

## 왜 뼈대만인가

이번 목적은 **다른 사람이 이 타입을 import 해 호출부(InfraStruct 파이프라인, Validator)를 먼저
엮을 수 있게** 하는 것이다. `InfraStruct` · `CurrentStateStore` 뼈대와 같은 성격이다.

실제 변환 로직은 미해결 **설계 전제** 두 가지가 서야 채울 수 있어 이번 범위에서 뺐다
(`plan.md` §4, resource-state-classes summary §87):

1. **`BehaviorHandler.handle` 시그니처가 아직 임시**(`void handle(T, Object)`)다. 상태 클래스가
   불변이라 타입만 좁혀선 동작하지 않고, 반환형까지 정하는 설계 판단(후보:
   `ScannedResourceState handle(T, ScannedResourceState)`)이 필요하다. 실제 호출부를 써 보며
   확정한다.
2. **핸들러 레지스트리**(어떤 어노테이션을 어느 핸들러로 보낼지)의 주입 경로가 아직 없다
   (ModuleRegistry 계열, 다른 브랜치).

시그니처만 못 박아 호출부를 언블록했다. 채울 자리는 `create()` 의 `// TODO` 로 표시했다.

## 남의 파일은 건드리지 않았다

summary §2 의 규율을 그대로 따랐다 — `BehaviorHandler.handle(T, Object)` 자리표시자 좁히기는
반환형까지 얽힌 설계 판단이고 이 스켈레톤의 산출물이 아니라, 실제 변환 로직을 쓰는 feature 로
미뤘다.

## 위치 — `internal` (spi 아님)

`Comparator` · `CurrentStateStore` 와 같은 `internal` 패키지. 상태 그릇(`ScannedResources` /
`DesiredResources`)만 `spi` 에 있고, 그 그릇을 다루는 엔진 모듈은 `internal` 에 둔다.

## 다음

- 실제 변환 구현: §4 의 `handle` 시그니처 확정 + 핸들러 레지스트리 주입 + `capturedAnnotations`
  소비 → `config` 반영 + 변환 라운드트립 테스트. 별도 feature.
- 생성자에 레지스트리 주입은 그 주입 경로가 선 뒤에 함께 정한다 (지금은 인자 없는 생성자).
