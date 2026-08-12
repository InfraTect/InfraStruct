# summary: resource-state-classes

브랜치: `feat/resource-state-classes`
담당: 선현진 · WBS `자원의 상태 관리 클래스 개발` (2026-08-12 → 08-13)

## 무엇을 만들었나

| 타입 | 종류 | 패키지 | 파일 |
|---|---|---|---|
| `ResourceState` | abstract class | spi | `spi/ResourceState.java` |
| `ScannedResourceState` | class | spi | `spi/ScannedResourceState.java` |
| `DesiredResourceState` | class | spi | `spi/DesiredResourceState.java` |
| `CurrentResourceState` | class | spi | `spi/CurrentResourceState.java` |
| `ScannedResources` | record | spi | `spi/ScannedResources.java` |
| `DesiredResources` | record | spi | `spi/DesiredResources.java` |
| `CurrentResources` | record | spi | `spi/CurrentResources.java` |

테스트 7개 파일 11개 테스트 추가. 레포 전체 **29 테스트 그린** (기존 18 → 29).

### 함께 바뀐 기존 코드

- `internal/CapturedAnnotation.java` → **`spi/CapturedAnnotation.java`** (패키지 이동).
  `internal` 에는 이제 `package-info.java` 만 남는다.
- `BehaviorHandler.handle(T, Object)` → **`handle(T, ScannedResourceState)`** (자리표시자 좁힘).
  `2026-08-11-annotation-handler-classes/summary.md` 의 "다음" 항목을 닫았다.
- 위 시그니처 변경에 따라 `BehaviorTest`·`CapturedAnnotationTest`·`BehaviorHandlerTest` 픽스처 수정.

## 확정된 계약

```java
public abstract class ResourceState {
    protected ResourceState();                            // Gson 역직렬화용
    protected ResourceState(Kind kind, String logicalId);
    public Kind getKind();
    public String getLogicalId();
    public Map<String, Object> getConfig();               // 살아 있는 컬렉션
    public List<String> getDependencies();                // 〃
    public Set<String> getRequiredFields();               // 〃
}

public class ScannedResourceState extends ResourceState {
    public List<CapturedAnnotation> getCapturedAnnotations();   // 살아 있는 컬렉션
}
public class DesiredResourceState extends ResourceState { }     // 추가 필드 없음
public class CurrentResourceState extends ResourceState {
    public String getPhysicalId();
    public void setPhysicalId(String physicalId);
}

public record ScannedResources(List<ScannedResourceState> scannedResources) { /* null 정규화 + 불변 복사 */ }
public record DesiredResources(List<DesiredResourceState> desiredResources) { /* 〃 */ }
public record CurrentResources(List<CurrentResourceState> currentResources) { /* 〃 */ }
```

## 왜 이렇게 했나 (핵심)

- **패키지는 7개 모두 `spi`.** 다이어그램은 "프레임워크 내부" 색이지만, `BehaviorHandler`·
  `Validator`·`Applier` 의 시그니처가 이 타입들을 그대로 노출한다 → 프로바이더가 직접 쥔다 →
  `CONTRIBUTING.md` §2 의 판별 규칙상 `spi`. `internal` 에 두면 프로바이더가 `internal` 을
  import 해야 해서 "internal 은 바꿔도 아무도 안 깨진다"는 전제가 무너진다.
  - 이 결정 때문에 `CapturedAnnotation` 도 따라 옮겼다. `ScannedResourceState`(spi) 의 필드
    타입이 `internal` 에 있으면 의존 방향이 거꾸로다.
- **상태 4개는 가변, 컨테이너 3개는 불변.** `BehaviorHandler.handle` 이 `void` 라 핸들러는
  넘겨받은 상태를 직접 고칠 수밖에 없다 → 상태의 컬렉션 접근자는 방어 복사를 하지 않는다.
  반대로 컨테이너는 각 단계가 새로 만들어 넘기지 덧붙이지 않으므로 불변이어도 된다.
- **`kind` 는 `Kind` 타입 유지.** Gson 은 인터페이스 필드를 역직렬화하지 못하지만(실측:
  `JsonIOException: Interfaces can't be instantiated!`), **쓰는 쪽은 이미 문자열**로 나간다
  (`"kind":"EC2"`). Terraform·Pulumi·K8s 도 "문자열 판별자 + 레지스트리 해석" 방식이다.
  읽는 쪽은 `CurrentStateStore` 가 `TypeAdapter<Kind>` 로 푼다 → 자세한 내용은 `plan.md` §3.
- **인자 없는 생성자를 둔 이유**: 없으면 Gson 이 `Unsafe` 로 인스턴스를 만들어 컬렉션 초기화를
  건너뛰고 `config` 가 `null` 인 객체가 나온다.
- **`LinkedHashMap`/`LinkedHashSet`**: 상태 파일(JSON) 의 diff 가 실행마다 흔들리지 않아야 한다.
- **세터는 `physicalId` 하나뿐.** kind·logicalId 는 스캔 시점에 확정되는 정체성 값이고 바뀌면
  Comparator 의 매칭 기준이 무너진다. physicalId 만 클라우드가 apply 이후에 발급한다.

## 빌드에서 배운 것 (예상과 달랐던 부분)

1. **SpotBugs `EI_EXPOSE_REP` 는 상태 클래스가 아니라 컨테이너 record 에서 났다.** 상태 쪽은
   억제 3개로 의도를 명시했고, 컨테이너 쪽 6건(EI + EI2 × 3)은 **억제하지 않고
   compact 생성자에서 `List.copyOf` 로 실제 불변화**해서 없앴다 — `plan.md` 가 "컨테이너는
   불변" 이라고 적어 둔 것을 코드가 실제로 지키게 만든 쪽이 맞다고 판단했다.
2. **compact 생성자에서 `null` → `List.of()` 정규화**를 함께 넣었다. 상태 파일에 키가 없으면
   Gson 이 `null` 을 넘기는데, `List.copyOf(null)` 은 NPE 라 파일이 비었을 때 터진다.
3. ⚠️ **Spotless 는 Gradle 데몬 JVM 에서 돈다 — toolchain(21) 이 아니다.** 데몬이 JDK 11 이면
   google-java-format 이 `record` 를 파싱하지 못해
   `FormatterException: class, interface, or enum expected` 로 죽는다. 컴파일은 toolchain 이
   21로 하므로 **컴파일은 되는데 포맷만 깨지는** 헷갈리는 실패다. `CONTRIBUTING.md` §1 의
   "Gradle 을 띄우는 JVM 도 21이어야 한다"가 이 경우다.

## 다음 (이 브랜치 밖)

- **ResourceScanner** (선현진, 08-14) — 이 그릇들을 실제로 채운다.
- **CurrentStateStore** (한정연, 08-14) — `TypeAdapter<Kind>` 가 필요하고, 그 어댑터는
  프로바이더 컨텍스트를 요구하므로 이 클래스가 `ModuleRegistry`(또는 해석된 프로바이더)에
  의존하게 된다. 상태 파일에 `provider` 문자열도 함께 저장하면 파일이 자기 설명적이 된다.
- `requiredFields` 는 Scanned·Desired 만 채운다는 규약이 아직 **문서상 약속**이다. Validator 가
  생기면 그쪽에서 강제할지 검토.
