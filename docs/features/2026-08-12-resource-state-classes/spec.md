# spec: resource-state-classes

`plan.md` 를 테스트 가능한 **행동 목록**으로 옮긴 것. 아래 `- ` 불릿이 첫 `/red` 때
체크리스트로 등록되고 위에서부터 red→green 을 돈다.

> 상태 클래스는 로직이 없는 그릇이다. 그래서 "필드를 들고 있다"가 아니라
> **"뒤 단계가 기대하는 방식으로 쓸 수 있다"** 를 행동으로 잡는다.
> 특히 **가변성**은 `BehaviorHandler.handle` 이 동작하기 위한 전제라 반드시 테스트로 고정한다.

## 행동 목록 (red 사이클 순서)

- ResourceState 하위 타입은 생성자로 받은 kind·logicalId 를 접근자로 그대로 돌려준다
- ResourceState 의 config·dependencies·requiredFields 는 빈 컬렉션으로 시작하고, 접근자로 얻은 컬렉션에 넣은 값이 상태에 반영된다
- ScannedResourceState 는 capturedAnnotations 를 빈 목록으로 시작하고 거기에 담은 CapturedAnnotation 을 돌려준다
- DesiredResourceState 는 ResourceState 의 계약(kind·logicalId·config)을 그대로 만족한다
- CurrentResourceState 는 physicalId 를 보유하고, apply 이후에 설정할 수 있다
- ScannedResources·DesiredResources·CurrentResources 는 생성자로 받은 목록을 그대로 돌려준다
- 컨테이너는 `null` 목록을 빈 목록으로 정규화한다
- 컨테이너는 생성 이후 원본 목록이 바뀌어도 영향을 받지 않는다
- BehaviorHandler 구현체가 넘겨받은 ScannedResourceState 의 config 를 직접 수정할 수 있다

## 공개 인터페이스 시그니처 (확정)

전부 `com.infrastruct.spi`. 배치 근거는 `plan.md` §3.

```java
public abstract class ResourceState {
    protected ResourceState() {}                          // Gson 역직렬화용
    protected ResourceState(Kind kind, String logicalId);

    public Kind getKind();
    public String getLogicalId();
    public Map<String, Object> getConfig();               // 살아 있는 컬렉션 (방어 복사 안 함)
    public List<String> getDependencies();                // 〃
    public Set<String> getRequiredFields();               // 〃
}

public class ScannedResourceState extends ResourceState {
    public ScannedResourceState() {}
    public ScannedResourceState(Kind kind, String logicalId);
    public List<CapturedAnnotation> getCapturedAnnotations();
}

public class DesiredResourceState extends ResourceState {
    public DesiredResourceState() {}
    public DesiredResourceState(Kind kind, String logicalId);
}

public class CurrentResourceState extends ResourceState {
    public CurrentResourceState() {}
    public CurrentResourceState(Kind kind, String logicalId, String physicalId);
    public String getPhysicalId();
    public void setPhysicalId(String physicalId);         // apply 후에 확정되는 값
}

// 컨테이너 3종은 compact 생성자에서 null 정규화 + 불변 복사를 한다.
public record ScannedResources(List<ScannedResourceState> resources) {
    public ScannedResources {
        resources = resources == null ? List.of() : List.copyOf(resources);
    }
}
public record DesiredResources(List<DesiredResourceState> resources) { /* 〃 */ }
public record CurrentResources(List<CurrentResourceState> resources) { /* 〃 */ }
```

### 이번에 함께 바뀌는 기존 코드

```java
// spi/BehaviorHandler.java — 자리표시자 좁히기
void handle(T annotation, ScannedResourceState state);   // 기존: Object state

// internal/CapturedAnnotation.java → spi/CapturedAnnotation.java (패키지 이동)
```

## 설계 근거 메모 (행동 아님)

1. **컬렉션은 빈 컨테이너로 초기화한다.** 스캐너가 필드를 하나씩 발견하며 채우는 구조라
   `null` 로 두면 모든 호출부가 null 체크를 해야 한다.
2. **`LinkedHashMap`/`LinkedHashSet`/`ArrayList` 를 쓴다** — 순서가 안정적이어야
   상태 파일(JSON) 의 diff 가 흔들리지 않고, PlanCreator 의 출력도 재현 가능해진다.
3. **접근자는 방어 복사를 하지 않는다.** `BehaviorHandler.handle` 이 `void` 라
   핸들러가 넘겨받은 상태를 직접 고쳐야 하기 때문 (`plan.md` §2 "가변성").
4. **인자 없는 생성자를 둔다.** Gson 은 없으면 `Unsafe` 로 인스턴스를 만들어 컬렉션
   초기화를 건너뛴다 → `config` 가 `null` 인 객체가 나와 `CurrentStateStore` 가 터진다.
5. **kind·logicalId 에는 세터를 두지 않는다.** 스캔 시점에 확정되는 정체성 값이고,
   이후 바뀌면 Comparator 의 매칭 기준이 무너진다. `physicalId` 만 세터를 갖는다 —
   클라우드가 발급하므로 apply 이후에야 알 수 있는 유일한 값이기 때문.

## 검증 메모 (어떻게 테스트할지)

1. `ResourceState` 는 abstract 라 직접 못 만든다 → 테스트 안에 픽스처 하위 클래스를 두고
   부모 계약을 검증한다. 픽스처 `Kind` 는 기존 테스트들처럼 `enum TestKind implements Kind`.
2. 가변성은 "접근자로 꺼낸 컬렉션에 넣고 → 다시 접근자로 꺼내 확인" 으로 본다.
   `getConfig().put(...)` 후 `getConfig()` 에 그 값이 있으면 살아 있는 컬렉션이다.
3. 컨테이너 record 는 `List.of(...)` 로 만들어 접근자가 같은 목록을 돌려주는지 본다.
4. `BehaviorHandler` 는 기존 `BehaviorHandlerTest` 의 `RecordingHandler` 픽스처를
   `ScannedResourceState` 시그니처로 바꾸고, 핸들러가 `state.getConfig().put(...)` 으로
   실제 상태를 고치는 것까지 확인한다 (좁힌 시그니처가 쓸모 있다는 증거).
