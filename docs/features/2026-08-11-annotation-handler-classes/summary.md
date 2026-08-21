# summary: annotation-handler-classes

브랜치: `feat/Annotation-handler-related-class-impl`

## 무엇을 만들었나

| 타입 | 종류 | 패키지 | 파일 |
|---|---|---|---|
| `BehaviorHandler<T extends Annotation>` | interface | spi | `spi/BehaviorHandler.java` |
| `@Behavior` | annotation | spi | `spi/Behavior.java` |
| `CapturedAnnotation` | record | internal | `internal/CapturedAnnotation.java` |

테스트: `spi/BehaviorHandlerTest`, `spi/BehaviorTest`, `internal/CapturedAnnotationTest`.
총 3개 행동, 각 1테스트.

## 확정된 계약

- `interface BehaviorHandler<T extends Annotation> { void handle(T annotation, Object state); }`
- `@interface Behavior { Class<? extends BehaviorHandler<?>> handler(); }` — RUNTIME/ANNOTATION_TYPE
- `record CapturedAnnotation(Annotation anno, Class<? extends BehaviorHandler<?>> handlerClass)`

## 왜 이렇게 했나 (핵심)

- **패키지**: 확장 작성자가 구현/사용하는 `BehaviorHandler`·`@Behavior` 는 spi, 스캐너/엔진이
  쓰는 내부 데이터 `CapturedAnnotation` 은 internal.
- **`handle` 의 state 파라미터 = `Object`**: 원래 `ScannedResourceState` 인데 그 타입이 아직
  없다(다른 영역). **인스턴스 파라미터**라 `Class<?>` 가 아니라 `Object` 가 맞는 자리표시자.
  타입이 생기면 `Object` → `ScannedResourceState` 로 좁힌다.
- **`@Behavior` 는 ANNOTATION_TYPE**: 매크로 어노테이션 "선언"에 붙기 때문. RUNTIME 은 스캐너가
  리플렉션으로 읽어야 하기 때문.
- **`CapturedAnnotation` 은 record**: 불변 데이터 묶음이라 자연스럽고, record 접근자가 필드를
  읽으므로 SpotBugs UUF(안 쓰이는 public 필드) 오탐이 아예 안 난다 — 이 브랜치엔
  `spotbugs-annotations` 가 없어 `@SuppressFBWarnings` 를 못 쓰는데, record 가 그 문제를 우회.
- `handler()`/`handlerClass` 타입은 raw `BehaviorHandler` 대신 `BehaviorHandler<?>` 로 rawtype 회피.

## 다음(이 브랜치 밖)

- ~~`ScannedResourceState` 가 생기면 `BehaviorHandler.handle` 의 `Object` 를 그 타입으로 좁힌다.~~
  → ✅ 해결 (`fix/behavior-handler-mock-class`): `void handle(T, Object)` → `void handle(T, ScannedResourceState)`.
  반환형(`void`)은 그대로 열려 있다 — `resource-state-classes` summary 의 "DesiredStateCreator 를 맡는 사람에게" 참고.
- 실제 매크로 어노테이션/핸들러(`@AllowSSH`, `AllowSshHandler`)는 사용례 — 이 범위 아님.
