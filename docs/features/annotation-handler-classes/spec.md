# spec: annotation-handler-classes

`plan.md` 를 테스트 가능한 **행동 목록**으로 옮긴 것. 아래 `- ` 불릿이 첫 `/red` 때
체크리스트로 등록되고 위에서부터 red→green 을 돈다.

> 어노테이션은 "RUNTIME 유지 + 올바른 @Target + 속성값 왕복"을 한 행동(한 테스트)으로 묶는다.

## 행동 목록 (red 사이클 순서)

- BehaviorHandler 는 제네릭 핸들러 인터페이스이며 구현체가 handle(어노테이션, 상태)을 실행할 수 있다
- @Behavior 는 RUNTIME/ANNOTATION_TYPE 이며 handler() 로 핸들러 클래스를 리플렉션으로 읽을 수 있다
- CapturedAnnotation 은 anno/handlerClass 를 담고 접근자로 그대로 돌려준다

## 공개 인터페이스 시그니처 (확정)

```java
// spi
public interface BehaviorHandler<T extends Annotation> {
    void handle(T annotation, Object state); // state: 원래 ScannedResourceState (없어서 Object)
}

// spi
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface Behavior {
    Class<? extends BehaviorHandler<?>> handler();
}

// internal
public record CapturedAnnotation(
        Annotation anno, Class<? extends BehaviorHandler<?>> handlerClass) {}
```

## 검증 메모 (어떻게 테스트할지 — 행동 아님, 그래서 번호 목록)

1. BehaviorHandler: 테스트용 어노테이션 `@interface FixtureAnno {}` 와 그걸 처리하는
   `class FixtureHandler implements BehaviorHandler<FixtureAnno>` 픽스처를 두고,
   `handle(annoInstance, someState)` 호출이 넘긴 값을 처리(예: 필드에 기록)하는지 확인.
2. @Behavior: `@Behavior(FixtureHandler.class) @interface FixtureMacro {}` 픽스처에서
   `FixtureMacro.class.getAnnotation(Behavior.class)` 가 null 아님(RUNTIME) +
   `handler()` == FixtureHandler.class + `@Behavior` 의 @Target 에 ANNOTATION_TYPE 포함.
3. CapturedAnnotation: `new CapturedAnnotation(anno, FixtureHandler.class)` 후
   `anno()`, `handlerClass()` 가 넣은 값을 그대로 돌려주는지 확인.
