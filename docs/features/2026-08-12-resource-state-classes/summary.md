# summary: resource-state-classes

## 무엇을 만들었나

파이프라인이 주고받는 자원 상태 그릇 **7종**을 `com.infrastruct.spi` 에 확정했다.

| 타입 | 상태 | 비고 |
|---|---|---|
| `ResourceState` | Javadoc 교체 | PR #19 스켈레톤의 구조 유지 |
| `DesiredResourceState` | Javadoc 교체 | 〃 |
| `CurrentResourceState` | Javadoc 교체 | 〃 |
| `DesiredResources` | Javadoc 교체 | 〃 |
| `CurrentResources` | Javadoc 교체 | 〃 |
| `ScannedResourceState` | **신규** | `capturedAnnotations` 보유 |
| `ScannedResources` | **신규** | |

**다른 사람이 만든 파일은 한 곳도 건드리지 않았다.** `BehaviorHandler` 와
`CapturedAnnotation` 정리는 둘 다 이번 범위 밖이라 뺐다 (근거: `plan.md` §3, §4).

테스트를 새로 추가했고 전체 **55개 전부 통과**한다.

## 확정된 계약

`spec.md` "공개 인터페이스 시그니처" 참조. 핵심 두 줄:

```java
// 상태는 불변이다. 접근자는 전부 불변 컬렉션을 돌려준다.
public Map<String, Object> config();

// 스캔 단계에서만 아직 소비되지 않은 어노테이션을 함께 들고 있다.
public List<CapturedAnnotation> capturedAnnotations();
```

## 왜 이렇게 했나

### ① PR #19 의 불변 스켈레톤을 갈아엎지 않고 따랐다

이 작업은 PR #21 로 먼저 올라갔지만 머지되지 못했고, 그 사이 PR #19(Comparator)가 자기
테스트를 돌리려고 상태 클래스 5종의 임시 스켈레톤을 `dev` 에 함께 넣었다. PR #21 의 설계는
**가변**(살아 있는 컬렉션 + `getX()` 접근자), 스켈레톤은 **불변**(전인자 생성자 + `x()` 접근자)
이라 정면으로 충돌했다.

이미 `dev` 에서 돌고 있는 쪽을 진실로 삼았다. 결과적으로 `Comparator.java` ·
`ComparatorTest` · `ResourceChangeTest` 를 **한 글자도 고치지 않았고**, #19 가 넣은 테스트
20개가 그대로 통과한다. 뒤늦게 올라가는 쪽이 맞추는 편이 협업 비용이 싸다.

### ② 남의 파일은 건드리지 않았다

한번은 `BehaviorHandler.handle` 의 `Object` 자리표시자를 좁히고 `CapturedAnnotation` 을
`spi` 로 옮겼다가 **둘 다 되돌렸다.** 이 작업의 산출물은 상태 클래스 7종이고 그 둘은 거기
포함되지 않는다.

되돌린 뒤에야 분명해진 것: 자리표시자를 좁히는 일은 단순한 타입 교체가 아니라 **설계 질문**이다.
①에서 불변을 택했으므로 타입만 좁히면 핸들러가 아무것도 반영하지 못하고, 반환형까지 바꾸려면
`handle` 을 실제로 부르는 코드가 있어야 판단이 선다. 지금은 그 호출부가 없다.
DesiredStateCreator 를 쓰는 사람이 정하는 편이 맞다.

### ③ `EI_EXPOSE_REP` 억제가 필요 없어졌다

접근자가 `Map.copyOf`/`List.copyOf` 로 만든 불변 컬렉션을 돌려주므로 SpotBugs 가 애초에
경고하지 않는다. `ProviderResource` 의 억제 주석이 *"상태 클래스들에서 같은 패턴이 반복되면
프로젝트 정책으로 재검토"* 라고 예고했던 상황은 **발생하지 않았다.** 이 저장소에서
`@SuppressFBWarnings` 는 여전히 `ProviderResource` 한 곳뿐이다.

## 다음

**`CapturedAnnotation` 의 패키지는 손대지 않았다.** `spi` 의 `ScannedResourceState` 가
`internal` 의 타입을 public 접근자로 돌려주는 상태가 남아 있다. 규칙 위반이지만 강제 장치가
없어 빌드는 통과하고, 상태 클래스와 별개의 판단이 필요한 변경이라 **별도 PR 로 뺐다.**
(`plan.md` §4)

| 할 일 | 기한 |
|---|---|
| **ResourceScanner** — 이 7종을 리플렉션으로 채운다 (ClassGraph) | 08-14 ~ 08-21 |
| `CurrentStateStore` 의 `TypeAdapter<Kind>` + **불변 상태의 Gson 역직렬화 대응** | 08-14 전 공유 필요 |
| `DesiredStateCreator` — `capturedAnnotations` 소비 + `handle` 시그니처 확정 | |
| `RegisterProvider.validator()` / `applier()` 자리표시자 좁히기 | 각자 작업 후 |

### ⚠️ CurrentStateStore 를 맡는 쪽에 먼저 전달할 것

상태 클래스가 불변이라 **인자 없는 생성자가 없다.** Gson 은 이 경우 `Unsafe` 로 인스턴스를
만들고 `final` 필드를 리플렉션으로 채우므로 읽기 자체는 되지만 생성자의 `copyOf` 정규화를
건너뛴다. 상태 파일에 `"config": null` 이 있으면 `config` 가 `null` 인 객체가 나온다.
`CurrentStateStore` 에서 `InstanceCreator` 를 등록하거나 읽은 뒤 정규화하는 편이 안전하다.
(`plan.md` §7)

### DesiredStateCreator 를 맡는 사람에게

`BehaviorHandler.handle` 의 파라미터는 `fix/behavior-handler-mock-class` 에서
`void handle(T, Object)` → `void handle(T, ScannedResourceState)` 로 좁혔다. 다만 상태가
불변이라 **파라미터 타입만 좁혀서는 동작하지 않는다** — 핸들러가 넘겨받은 상태를 고칠 수 없기
때문이다. 반환형은 아직 `void` 이고 이 부분은 그대로 열려 있다. 반환형까지 정하면:

```java
ScannedResourceState handle(T annotation, ScannedResourceState state);   // 후보
```

이러면 어노테이션이 여러 개인 자원은 반환값을 다음 핸들러의 입력으로 이어 붙이는 형태가 된다:

```java
ScannedResourceState acc = scanned;
for (CapturedAnnotation captured : scanned.capturedAnnotations()) {
    acc = handlerFor(captured).handle(captured.anno(), acc);
}
```

실제 호출부를 쓰면서 이 모양이 편한지 확인한 뒤 확정하면 된다.
