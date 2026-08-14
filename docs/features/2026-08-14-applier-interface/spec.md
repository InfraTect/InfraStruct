정# spec: applier-interface

`plan.md` 를 테스트 가능한 **행동 목록**으로 옮긴 것. 아래 `- ` 불릿이 첫 `/red` 때
체크리스트로 등록되고 위에서부터 red→green 을 돈다.

> ⚠️ 하네스 주의: `resync_behaviors` 는 spec.md 의 **모든 `- ` 줄**(들여쓴 것 포함)을 행동으로
> 등록한다. 그래서 "행동 목록" 밖에서는 `- ` 를 쓰지 않는다 (검증 메모·범위 밖은 번호/괄호 사용).

> 순서 주의: **Applier 인터페이스를 먼저** 만든다(1번). 2번(상한 좁히기)의 테스트가
> `Applier` 타입을 참조하므로 1번이 먼저 존재해야 한다.

## 행동 목록 (red 사이클 순서)

- Applier 는 apply(plan, current) 를 실행해 새 CurrentResources 를 돌려주는 계약이다
- @RegisterProvider.applier() 의 상한이 Class<? extends Applier> 로 좁혀졌고 Applier 구현 클래스를 리플렉션으로 읽을 수 있다

## 공개 인터페이스 시그니처 (확정)

```java
// spi — 신규
public interface Applier {
    CurrentResources apply(OrderedResourceChangeSet plan, CurrentResources current);
}

// spi — applier() 상한 좁히기 (나머지는 그대로)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RegisterProvider {
    String providerId();
    Class<?> validator();                 // 그대로 (Validator 타입 아직 없음)
    Class<? extends Applier> applier();    // Class<?> 에서 좁힘, TODO 주석 제거
}
```

## 검증 메모 (어떻게 테스트할지 — 행동 아님, 그래서 번호 목록)

1. **Applier**: 넘어온 값을 기록하고 지정한 상태를 돌려주는
   `class RecordingApplier implements Applier` 픽스처를 두고, `apply(plan, current)` 호출이
   (a) 넘긴 `plan`/`current` 를 그대로 받았는지(필드에 기록해 `isSameAs` 로 확인),
   (b) 미리 정해 둔 `CurrentResources` 를 반환하는지 를 검증.
   `plan`/`current` 는 빈 목록으로 만든 최소 인스턴스면 충분
   (`new OrderedResourceChangeSet(List.of())`, `new CurrentResources(List.of())`).

2. **@RegisterProvider.applier() 상한**: 기존 `RegisterProviderTest` 의 픽스처
   `DummyApplier {}` 를 `class DummyApplier implements Applier { ... apply 구현 ... }` 로 바꾼다.
   (a) 기존 왕복 검증 유지: `anno.applier()` == `DummyApplier.class`.
   (b) 상한이 실제로 좁혀졌음을 관측: 어노테이션 메서드의 **제네릭 반환 타입**을 리플렉션으로 읽어
   와일드카드 상한이 `Applier` 인지 확인 —
   ```java
   Type ret = RegisterProvider.class.getMethod("applier").getGenericReturnType();
   Type arg = ((ParameterizedType) ret).getActualTypeArguments()[0]; // ? extends Applier
   Type bound = ((WildcardType) arg).getUpperBounds()[0];
   assertThat(bound).isEqualTo(Applier.class);
   ```
   좁히기 전(`Class<?>`)에는 상한이 `Object` 라 이 단언이 **실패(RED)**, 좁힌 뒤 **통과(GREEN)**.

## 범위 밖 (plan.md §6 재확인)

1. 실제 프로바이더 Applier 구현체, `Validator` 인터페이스, 파이프라인 배선, apply 실패 시맨틱.
2. `validator()` 상한은 이번에 손대지 않는다.
