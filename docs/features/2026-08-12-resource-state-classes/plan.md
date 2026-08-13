# plan: resource-state-classes

## 목표

파이프라인이 단계마다 주고받는 **자원 상태 그릇 7종**을 확정한다. 로직은 없다 — 뒤 단계
(Validator / Comparator / Applier / CurrentStateStore)가 기대하는 모양을 타입으로 못 박는 것이
전부다. 이 7개가 뒤따르는 5개 작업의 공통 선행이라 시그니처를 먼저 굳혀야 한다.

## 1. 전제 — PR #19 가 이미 심어 둔 스켈레톤을 따른다

이 작업은 원래 PR #21 로 먼저 올라갔으나 머지되지 못했다. 그 사이 PR #19(Comparator)가
자기 테스트를 돌리기 위해 상태 클래스 5종의 **임시 스켈레톤**을 `dev` 에 함께 넣고 머지됐다.

다섯 클래스 모두 Javadoc 첫 줄에 "임시 스켈레톤이며 상태 관리 작업에서 실제 구현으로
교체될 예정" 이라고 적혀 있다.

그래서 선택지는 두 개였다:

| | 내용 | 결과 |
|---|---|---|
| A | PR #21 의 **가변** 설계로 스켈레톤을 갈아엎는다 | `Comparator` 2줄 + 테스트 4곳 수정, 남의 머지된 설계를 뒤집음 |
| B | **PR #19 의 불변 설계를 계약으로 받아들이고** 빠진 2종을 거기 맞춘다 | 기존 호출부 수정 0곳 |

**B 를 택했다.** 이미 `dev` 에서 돌고 있는 코드가 진실이고, 뒤늦게 올라가는 쪽이 맞추는 것이
협업 비용이 가장 싸다. 실제로 이 선택 덕분에 `Comparator.java` 와 `ComparatorTest` /
`ResourceChangeTest` 는 **한 글자도 고치지 않았다.**

따라서 이 작업에서 스켈레톤 5종은 **구조를 바꾸지 않고** Javadoc 만 실제 설명서로 교체한다.

## 2. 불변(immutable) — 이 설계의 중심 결정

PR #19 의 스켈레톤은 `final` 필드 + 전인자 생성자 + `Map.copyOf` 다. 이것을 계약으로 받는다.

- 파이프라인의 각 단계는 앞 단계 상태를 **고치지 않고 새 상태를 만들어** 넘긴다.
- Comparator 가 비교하는 도중 대상이 바뀔 여지가 없다.
- 접근자가 불변 컬렉션을 돌려주므로 `EI_EXPOSE_REP` 억제가 **원천적으로 필요 없다.**
  (`ProviderResource` 가 예고했던 "상태 클래스에서 억제 패턴이 반복되면 재검토" 상황이
  아예 발생하지 않는다.)

### 접근자 이름은 `getX()` 가 아니라 `x()`

스켈레톤이 record 스타일(`config()`, `logicalId()`)로 잡혀 있고 `Comparator` 가 그렇게 부른다.
`spi` 의 `ResourceChange` · `FieldDiff` 와 `internal` 의 `CapturedAnnotation` 이 전부 record 라
`x()` 로 통일돼 있으므로, 클래스 4종만 `getX()` 를 쓰면 SPI 표면이 두 가지 말투로 갈린다.

## 3. 남겨 둔 것 — `BehaviorHandler.handle` 의 자리표시자

`BehaviorHandler.handle` 은 아직 `Object` 자리표시자를 쓴다.

```java
void handle(T annotation, Object state);          // 그대로 둔다
```

`ScannedResourceState` 가 생겼으니 좁힐 수는 있지만, **이번 범위 밖이라 손대지 않는다.**
이 작업의 산출물은 상태 클래스 7종이고 `BehaviorHandler` 는 그중 하나가 아니다. 이전 feature
문서도 이 정리를 "다음(이 브랜치 밖)" 으로 분류해 뒀다.

**단순히 미룬 것이 아니라 설계 질문이 하나 열려 있다.** §2 에서 상태를 불변으로 잡았으므로
타입만 `ScannedResourceState` 로 좁히면 핸들러가 넘겨받은 상태를 고칠 수 없어 "컴파일은 되는데
아무것도 반영하지 못하는 SPI" 가 된다. 좁히는 쪽은 반환형까지 같이 정해야 한다:

```java
ScannedResourceState handle(T annotation, ScannedResourceState state);   // 후보
```

이러면 DesiredStateCreator 가 반환값을 다음 핸들러의 입력으로 이어 붙이는 형태가 된다.
**이 판단은 실제 호출부(DesiredStateCreator)를 쓰는 사람이 하는 것이 맞다** — 지금은 `handle`
을 부르는 코드가 하나도 없어 어떤 모양이 편한지 확인할 방법이 없다.

## 4. 남겨 둔 것 — `CapturedAnnotation` 의 패키지

`ScannedResourceState` 가 `spi` 인데 그 필드 타입인 `CapturedAnnotation` 은 `internal` 에 있다.
`capturedAnnotations()` 가 public 이라 프로바이더가 이 접근자를 쓰면 `internal` 을 import 하게
되고, `internal/package-info.java` 가 선언한 "외부 노출 금지 · 누구도 여기에 의존하지 않는다"가
깨진다.

**그럼에도 이번 PR 에서는 옮기지 않는다.** 이 작업의 범위는 상태 클래스 7종이고,
`CapturedAnnotation` 은 다른 사람이 별도 PR 로 만든 타입이다. 패키지 이동은 그 자체로 판단이
필요한 변경이라 상태 클래스 리뷰에 섞지 않는 편이 낫다.

강제하는 장치는 없다 — JPMS 도, ArchUnit 도, checkstyle 의 `ImportControl` 도 없다
(`IllegalImport` 는 `sun.*` 만 막는다). 따라서 컴파일·CI 는 통과한다. 규칙 위반이지 빌드
실패가 아니므로 **별도 PR 로 분리해 처리한다.**

## 5. 패키지 배치 — 7개 모두 `spi`

다이어그램은 "프레임워크 내부 코드" 색이지만, `CONTRIBUTING.md` §2 의 판별 규칙
("엔진 밖의 누군가가 이 타입을 코드에서 직접 쥐는가?")으로는 `spi` 다:

| SPI 타입 (프로바이더가 구현) | 시그니처에 등장하는 상태 타입 |
|---|---|
| `Validator.validate(DesiredResources)` | `DesiredResources` |
| `Applier.apply(…, CurrentResources)` | `CurrentResources` |
| `BehaviorHandler.handle` | `ScannedResourceState` (§3 에서 좁히면) |

`internal` 에 두면 프로바이더가 `internal` 을 import 해야 하고 "internal 은 바꿔도 아무도 안
깨진다"는 전제가 무너진다. PR #19 도 이미 5종을 `spi` 에 뒀다.

## 6. 범위 밖

- **ResourceScanner** — 이 7종을 채우는 쪽. 바로 다음 작업(08-14~).
- **DesiredStateCreator** — `capturedAnnotations` 를 소비해 `config` 로 옮기는 변환.
- **CurrentStateStore 의 `TypeAdapter<Kind>`** — §7 참조.
- `Comparator` / `ResourceChange` 계열 — 이미 머지됨(PR #19). 손대지 않는다.

## 7. CurrentStateStore 를 맡는 쪽에 넘길 것 — Gson 과 불변 상태

`kind` 는 `Kind`(인터페이스) 타입을 유지한다. Gson 2.11 실측상 직렬화는 `"kind":"EC2"` 로 잘
되고 역직렬화만 `JsonIOException: Interfaces can't be instantiated!` 로 깨지므로, 읽는 쪽만
`CurrentStateStore` 가 `TypeAdapter<Kind>` 로 푼다 (providerId → 그 프로바이더의 Kind enum →
`value()` 매칭). 앱이 프로바이더를 하나만 선언하므로 가능하다.

**추가로 알려야 할 것**: 상태 클래스가 불변이라 인자 없는 생성자가 없다. Gson 은 이 경우
`Unsafe` 로 인스턴스를 만들고 `final` 필드를 리플렉션으로 채우므로 **읽기 자체는 동작하지만
생성자의 `copyOf` 정규화를 건너뛴다.** 즉 상태 파일에 `"config": null` 이 있으면 `config` 가
`null` 인 객체가 나온다. `CurrentStateStore` 에서 `InstanceCreator` 를 등록하거나 읽은 뒤
정규화하는 편이 안전하다. 08-14 전에 전달한다.

## 8. 검증 관점

상태 클래스는 로직이 없는 그릇이라 "필드를 들고 있다"가 아니라 **"뒤 단계가 기대하는 방식으로
쓸 수 있다"** 를 행동으로 잡는다. 특히 §2 의 **불변성**은 이 설계의 전제이므로 반드시 테스트로
고정한다 — 생성 후 원본 컬렉션을 고쳐도 안 새는지, 접근자가 돌려준 컬렉션이 수정을 거부하는지.
행동 목록은 `spec.md`.
