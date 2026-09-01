# summary: desired-state-creator-impl

## 무엇을 만들었나

`DesiredStateCreator.create(ScannedResources)` 의 **실제 변환 로직**. 뼈대 feature
(`2026-08-14-desired-state-creator`)가 "설계 전제가 아직 안 섰다"며 비워 둔 본문을 채웠다.

```
ScannedResources ──자원마다──▶ capturedAnnotations 를 순서대로 핸들러에 먹인다 (fold)
                                          │
                                          ▼
                    DesiredResourceState ──▶ DesiredResources
```

| 파일 | 무엇 |
|---|---|
| `internal/DesiredStateCreator` | 본문 구현 (변환 + 진단) |
| `internal/DesiredStateException` | 신설. `ResourceScanException` 과 같은 모양 |
| `spi/BehaviorHandler` | `void handle(T, ScannedResourceState)` → **`ScannedResourceState handle(T, ScannedResourceState)`** |
| `spi/ScannedResourceState` | `withConfigEntry(String, Object)` 복사 헬퍼 신설 |
| `test/fixture/desired/` | 픽스처 11개 (어노테이션 2 + 핸들러 8 + Kind enum) |

테스트 15개(`DesiredStateCreatorTest` 12, `ScannedResourceStateTest`·`BehaviorHandlerTest` 갱신),
전체 `./gradlew check` 통과. 커버리지 97% (instruction).

## 뼈대가 남긴 숙제 둘을 닫았다

**① `handle` 시그니처 — 반환형을 상태로 (plan §4 안 A).** 상태는 불변인데 핸들러는 상태를 고쳐야
한다. `void` + 불변이면 핸들러가 할 수 있는 일이 없다. 반환값을 다음 핸들러의 입력으로 이어 붙이는
fold 로 파이프라인 전체의 불변 규율을 그대로 지켰다. 대가인 "핸들러 작성이 번거롭다"는
`withConfigEntry` 로 없앴다 — 6줄 재조립이 한 줄이 된다.

**② 핸들러 조회 — 레지스트리는 필요 없었다.** 뼈대 plan 은 *"조회 레지스트리의 주입 경로
(ModuleRegistry)가 없다"* 를 이유로 미뤘는데, 그 전제가 틀렸다. `CapturedAnnotation` 이 이미
`handlerClass` 를 들고 있다 — 스캐너가 `@Behavior` 를 읽는 시점에 답이 정해진다. 필요한 것은
`Class` → 인스턴스 하나뿐이고 리플렉션으로 지금 할 수 있다. **다른 브랜치에 블록되지 않았다.**

## 설계 결정

**핸들러 캐시는 `create()` 호출 지역에 둔다.** 필드로 올리면 이 모듈이 상태를 갖게 되어 스레드
안전성 논의가 생긴다. 파이프라인에서 `create()` 는 실행당 한 번이라 재사용으로 얻을 것도 없다.
같은 `Class` 는 한 번만 인스턴스화되어 자원들이 공유한다(핸들러 = 상태 없는 함수).

**비검사 캐스트는 `asAnnotationHandler` 한 곳에 가뒀다.** `CapturedAnnotation` 이 어노테이션과
핸들러 클래스를 따로 들고 있어 "이 핸들러가 이 어노테이션을 받는다"를 컴파일러가 알 수 없다.
대신 **부르기 전에** 핸들러가 선언한 타입 인자를 리플렉션으로 뽑아 대조한다 — 안 그러면
`ClassCastException` 이 핸들러 *안에서* 터져 사용자가 원인을 못 찾는다. 타입 인자를 확정할 수 없는
구현(raw, 상위 클래스가 대신 구현)은 검사를 건너뛰고 부른다.

**잠그는 것은 식별자(`kind`, `logicalId`)뿐이다.** `logicalId` 는 Comparator 의 매칭 키라 여기서
바뀌면 뒤 단계에서 "삭제 + 생성"으로 보인다. 반대로 `dependencies` 를 더하는 것은 매크로의 정상적인
일이라 허용한다.

**진단 메시지는 셋을 항상 담는다** — 자원 logicalId, 어노테이션 타입, 핸들러 FQCN. 없으면 "어느
자원의 어느 어노테이션이 문제인가"를 알 수 없다. `logicalId` 는 언제나 **원본 자원의 것**을 쓴다
(핸들러가 바꿔 돌려준 값을 쓰면 진단이 거짓말을 한다).

## 남의 파일을 건드린 것 — `spi` 둘

`spi` 는 프로바이더가 쥐는 표면이라 함부로 못 고치는 자리다. 이번엔 정당하다:
`BehaviorHandler.handle` 은 뼈대 feature 가 **명시적으로 "DesiredStateCreator 를 맡는 사람이
정한다"고 넘긴 숙제**였고, `withConfigEntry` 는 그 결정의 직접적인 결과다(안 열면 재조립 코드가
프로바이더마다 반복되고 그게 곧 버그 표면이다).

시그니처 변경으로 기존 테스트 4개(`BehaviorHandlerTest`, `BehaviorTest`,
`ScannedResourceStateTest`, `CapturedAnnotationTest`)의 핸들러 픽스처가 깨져 함께 고쳤다.

## 스펙과 달라진 점 — 사이클 순서

spec §8-0 이 예고한 상황이 실제로 나왔다. 행동 #6(같은 키는 나중 것이 이긴다)은 앞 사이클의
fold 구현이 **이미 만족**해서 어떤 테스트를 써도 RED 가 안 났다. 그래서:

1. #6 의 테스트는 #7(핸들러 인스턴스 재사용, 진짜 RED)과 **같은 사이클에 묶어** 썼다. 테스트 자체는
   남겼다 — 순서 규칙은 문서화된 계약이고, 나중에 병합 순서를 뒤집으면 이 테스트만이 잡는다.
2. 그 뒤로 행동 라벨과 사이클이 한 칸씩 밀렸다(사이클 N 에서 행동 N+1 의 테스트를 씀).
3. 밀린 한 칸은 **행동 #11 을 두 사이클로 쪼개** 흡수했다 — `logicalId` 잠금(+`dependencies` 허용)
   먼저, 그다음 `kind` 잠금. 실제 triangulation 이라 둘 다 진짜 RED 였다.

결과적으로 **12 개 행동 = 12 번의 진짜 red→green** 이 유지됐다.

`/qa` 에서 회귀 테스트 하나를 덧붙였다: 빈 입력 → 빈 출력. 스텁 시절부터 성립하던 성질이라 RED 를
만들 수 없어 행동 목록에서는 뺐던 것이다(spec §10-1).

## 검증하지 않은 것

spec §10 그대로다. 요약하면 **RED 를 만들 수 없어서** 뺀 것들이다: 결과에 어노테이션이 안 남는 것
(타입으로 보장) · 원본 `ScannedResources` 불변(불변 타입이라 구조적 보장) · raw 핸들러 경로 ·
상위 클래스가 `BehaviorHandler` 를 대신 구현한 핸들러.

## 다음

1. **배선 feature** — `InfraStruct.run()` 에서 `scan → create → validate` 를 잇는다. 그때
   `DesiredStateException` 과 `ResourceScanException` 을 `api` 로 올릴지 **함께** 본다(두 예외가
   독립적으로 같은 `internal` 결론에 도달했다).
2. **`withDependency` 등 복사 헬퍼** — 의존 관계를 더하는 핸들러가 실제로 나타나면 그때 6인자
   생성자에서 헬퍼로 승격한다. 지금은 어떤 모양이 편할지 고를 근거가 없다.
3. **핸들러 실행 순서 지정(우선순위)** — 필요해지면 `@Behavior` 에 속성을 더한다. 지금은 타입
   이름순 결정성으로 충분하다.
