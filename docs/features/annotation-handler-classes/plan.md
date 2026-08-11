# feature: annotation-handler-classes

브랜치: `feat/Annotation-handler-related-class-impl`

## 1. 목표 (무엇을)

리소스에 붙은 (매크로) 어노테이션을 처리하는 **3개 타입**을 만든다.

| # | 타입 | 종류 | 한 줄 설명 |
|---|---|---|---|
| 1 | `BehaviorHandler<T extends Annotation>` | interface | 매크로 어노테이션의 효과를 자원 상태에 반영하는 핸들러 계약. |
| 2 | `@Behavior` | annotation | 매크로 어노테이션 선언에 붙여, 그걸 처리할 핸들러를 지정. |
| 3 | `CapturedAnnotation` | record | 스캔 때 발견한 "어노테이션 + 그 핸들러 클래스" 쌍을 담는 데이터. |

맥락: 사용자가 자원에 `@AllowSSH` 같은 매크로 어노테이션을 붙이면, 스캐너가 그걸
`CapturedAnnotation` 으로 담고(→ ScannedResourceState), DesiredStateCreator 가
`handlerClass.handle()` 을 호출해 config 에 반영한다. (`@AllowSSH`/`AllowSshHandler` 는
사용례일 뿐 이번 구현 대상 아님.)

## 2. 각 타입의 계약 (다이어그램 기준 + 결정)

### `BehaviorHandler<T extends Annotation>`  ⚠️ 미구현 의존성
```java
public interface BehaviorHandler<T extends Annotation> {
    void handle(T annotation, Object state);   // state: 원래 ScannedResourceState
}
```
- 다이어그램: `handle(T, ScannedResourceState): void`.
- `ScannedResourceState` 는 아직 없다(다른 영역). **인스턴스 파라미터**라 자리표시자는
  `Object` 로 둔다 — 타입이 생기면 `Object` → `ScannedResourceState` 로 좁힌다.
  - 참고: task 2 의 `Class<?>` 와 다른 이유 — 거기선 "클래스 토큰"이었지만 여기 state 는
    "상태 *객체*"라 `Class<?>` 가 아니라 `Object` 가 맞다. (구현체가 없어 좁혀도 안전)

### `@Behavior`
```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface Behavior {
    Class<? extends BehaviorHandler<?>> handler();
}
```
- 매크로 어노테이션 "선언"에 붙으므로 `@Target(ANNOTATION_TYPE)`.
- 스캐너가 런타임에 읽어 핸들러를 찾으므로 `RUNTIME`.
- `handler()` 타입은 raw `BehaviorHandler` 대신 `BehaviorHandler<?>` 로 두어 rawtype 경고 회피.

### `CapturedAnnotation`
```java
public record CapturedAnnotation(
        Annotation anno, Class<? extends BehaviorHandler<?>> handlerClass) {}
```
- 다이어그램 필드: `anno: Annotation`, `handlerClass: Class<? extends BehaviorHandler>`.
- **record 로 만든다** (아래 §4 참조) — 불변 데이터 홀더라 자연스럽고, SpotBugs 문제도 피한다.

## 3. 패키지 배치 (제안 — 확정 필요)

| 타입 | 제안 | 근거 |
|---|---|---|
| `BehaviorHandler` | **spi** | 확장 작성자가 구현(implements). 다이어그램 gold 색. |
| `@Behavior` | **spi** | 매크로 어노테이션 선언에 붙이는 확장용. BehaviorHandler 와 한 쌍. |
| `CapturedAnnotation` | **internal** | 스캐너가 만들고 엔진이 소비하는 내부 데이터. 다이어그램 default(내부) 색. |

> `CapturedAnnotation` 의 internal 배치는 확인받고 싶다 — ScannedResourceState 에 담겨
> 핸들러가 간접적으로 볼 여지가 있어 spi 로 볼 수도 있다. 일단 internal 로 제안.

## 4. SpotBugs / 의존성 관련 (중요)

- 이 브랜치에는 아직 `spotbugs-annotations` 의존성이 **없다** (task 2 의 그 의존성은 dev PR 로
  분리됨). 따라서 `@SuppressFBWarnings` 를 쓸 수 없다.
- 다행히 이번 3개는 SpotBugs UUF(안 쓰이는 public 필드) 문제를 **원천적으로 안 만든다**:
  - `BehaviorHandler`(인터페이스), `@Behavior`(어노테이션) → 필드 없음.
  - `CapturedAnnotation` → **record** 로 만들면 접근자(anno(), handlerClass())가 필드를
    읽으므로 "안 쓰이는 필드"로 잡히지 않는다. (public 필드 클래스로 만들면 task 2 처럼
    UUF 가 나는데, 억제할 의존성이 이 브랜치엔 없다 → record 가 깔끔한 해법)

## 5. 이번 범위 밖 (하지 않는 것)

- `ScannedResourceState` 등 상태 타입 구현 (다른 영역).
- 실제 매크로 어노테이션/핸들러(`@AllowSSH`, `AllowSshHandler`) — 사용례일 뿐.
- 스캐너·DesiredStateCreator 가 이들을 실제로 엮는 로직.

## 6. 검증 관점 (spec 에서 테스트로)

- `BehaviorHandler`: 테스트용 핸들러가 `implements BehaviorHandler<SomeAnno>` 하고
  `handle(anno, state)` 호출이 동작.
- `@Behavior`: RUNTIME 유지 + `@Target` 에 ANNOTATION_TYPE + `handler()` 값 왕복(리플렉션).
- `CapturedAnnotation`: 생성 후 `anno()`, `handlerClass()` 접근자가 넣은 값을 돌려줌.
