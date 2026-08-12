# spec: resource-state-classes

`plan.md` 를 테스트 가능한 **행동 목록**으로 옮긴 것. 아래 `- ` 불릿이 체크리스트가 되고
위에서부터 red→green 을 돈다.

> 상태 클래스는 로직이 없는 그릇이다. 그래서 "필드를 들고 있다"가 아니라
> **"뒤 단계가 기대하는 방식으로 쓸 수 있다"** 를 행동으로 잡는다.
> 특히 **불변성**은 설계의 전제(`plan.md` §2)라 반드시 테스트로 고정한다.

## 행동 목록

### ResourceState (공통 부모)

- 하위 타입은 생성자로 받은 kind·logicalId·config·dependencies·requiredFields 를 접근자로 그대로 돌려준다
- 생성 이후 원본 컬렉션이 바뀌어도 상태는 영향을 받지 않는다
- 접근자가 돌려준 컬렉션은 수정을 거부한다 (`UnsupportedOperationException`)
- 컬렉션 인자에 `null` 을 넘기면 `NullPointerException` 이 난다

### ScannedResourceState

- capturedAnnotations 를 생성자로 받아 그대로 돌려준다

### DesiredResourceState

- 필드를 더하지 않고 ResourceState 의 계약을 그대로 만족한다

### CurrentResourceState

- physicalId 를 생성자로 받아 돌려준다
- apply 전이면 physicalId 가 `null` 일 수 있다
- requiredFields 는 이미 적용된 자원이라 비어 있다

### 컨테이너 3종 (ScannedResources / DesiredResources / CurrentResources)

- 생성자로 받은 목록을 그대로 돌려준다
- 생성 이후 원본 목록이 바뀌어도 영향을 받지 않는다
- `null` 목록을 넘기면 `NullPointerException` 이 난다

## 공개 인터페이스 시그니처 (확정)

전부 `com.infrastruct.spi`. 배치 근거는 `plan.md` §5.

```java
public abstract class ResourceState {
    protected ResourceState(Kind kind, String logicalId, Map<String, Object> config,
            List<String> dependencies, Set<String> requiredFields);

    public Kind kind();
    public String logicalId();
    public Map<String, Object> config();        // 불변
    public List<String> dependencies();         // 불변
    public Set<String> requiredFields();        // 불변
}

public final class ScannedResourceState extends ResourceState {
    public ScannedResourceState(Kind kind, String logicalId, Map<String, Object> config,
            List<String> dependencies, Set<String> requiredFields,
            List<CapturedAnnotation> capturedAnnotations);

    public List<CapturedAnnotation> capturedAnnotations();
}

public final class DesiredResourceState extends ResourceState {
    public DesiredResourceState(Kind kind, String logicalId, Map<String, Object> config,
            List<String> dependencies, Set<String> requiredFields);
}

public final class CurrentResourceState extends ResourceState {
    public CurrentResourceState(Kind kind, String logicalId, Map<String, Object> config,
            List<String> dependencies, Set<String> requiredFields, String physicalId);

    public String physicalId();                 // apply 전이면 null
}

// 컨테이너 3종은 compact 생성자에서 불변 복사를 한다.
public record ScannedResources(List<ScannedResourceState> resources) {
    public ScannedResources { resources = List.copyOf(resources); }
}
public record DesiredResources(List<DesiredResourceState> resources) { /* 〃 */ }
public record CurrentResources(List<CurrentResourceState> resources) { /* 〃 */ }
```

### 기존 코드는 건드리지 않는다

- `BehaviorHandler.handle` 의 `Object` 자리표시자는 그대로 둔다 (범위 밖, 근거: `plan.md` §3)
- `CapturedAnnotation` 은 `internal` 에 그대로 둔다 (범위 밖, 근거: `plan.md` §4)
- `Comparator` 와 그 테스트도 그대로다 — PR #19 의 API 를 따랐으므로 수정할 호출부가 없다

**이 작업이 더하는 것은 `spi` 의 상태 클래스 7종과 그 테스트뿐이다.**

## 검증 메모 (어떻게 테스트할지)

1. `ResourceState` 는 abstract 라 직접 못 만든다 → 테스트 안에 픽스처 하위 클래스
   (`FixtureState`)를 두고 부모 계약을 검증한다.
2. 픽스처 `Kind` 는 `ComparatorTest` 와 같은 스타일로 람다를 쓴다:
   `private static final Kind TEST_KIND = () -> "test-kind";`
3. 불변성은 두 각도로 본다 — ① 생성자에 넘긴 `ArrayList`/`HashMap` 을 나중에 고쳐도 상태가
   안 바뀌는지(입력 차단) ② 접근자가 돌려준 컬렉션에 `put`/`add` 하면 터지는지(출력 차단).
4. Mockito 는 쓰지 않는다. 순수 데이터 검증이라 목이 필요 없다.
