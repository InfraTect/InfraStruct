# spec: provider-related-classes

`plan.md` 를 테스트 가능한 **행동 목록**으로 옮긴 것. 아래 `- ` 불릿이 첫 `/red` 때
체크리스트로 등록되고 위에서부터 red→green 을 돈다.

> 어노테이션은 "RUNTIME 유지 + 올바른 @Target + 속성값 왕복"을 **한 행동(한 테스트)**으로
> 묶는다. 속성을 여러 행동으로 쪼개면 첫 구현이 나머지를 이미 충족해 RED 를 못 만든다.

## 행동 목록 (red 사이클 순서)

- Kind 는 value() 를 가진 인터페이스이며 enum 으로 구현할 수 있다
- Provider 는 상속 가능한 추상 베이스 클래스다
- ProviderResource 는 하위에서 kind/provider 필드를 설정하고 읽을 수 있다
- @Resource 는 RUNTIME/TYPE 이며 name() 값을 리플렉션으로 읽을 수 있다
- @Required 는 RUNTIME 이며 FIELD 에 붙고 리플렉션으로 읽을 수 있다
- @RegisterProvider 는 RUNTIME/TYPE 이며 providerId/validator/applier 를 리플렉션으로 읽을 수 있다

## 공개 인터페이스 시그니처 (확정)

```java
// spi
public interface Kind {
    String value();
}

// spi
public abstract class Provider {}

// spi
public abstract class ProviderResource {
    public Kind kind;
    public Class<? extends Provider> provider;
}

// spi
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RegisterProvider {
    String providerId();
    Class<?> validator();   // TODO: Class<? extends Validator> (타입 생기면 좁힘)
    Class<?> applier();           // TODO: Class<? extends Applier>
}

// api
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Resource {
    String name();
}

// spi
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Required {}
```

## 검증 메모 (어떻게 테스트할지 — 행동 아님, 그래서 번호 목록)

1. Kind: 테스트용 `enum TestKind implements Kind { EC2; public String value(){return name();} }`
   를 두고 `TestKind.EC2.value()` 확인.
2. Provider: `class TestProvider extends Provider {}` 가 컴파일·인스턴스화되는지.
3. ProviderResource: 하위 픽스처에서 `kind`, `provider` 를 세팅 후 읽어 확인.
4. 어노테이션 3종: 픽스처(클래스/필드)에 붙여 `getAnnotation(...)` 이 null 아님(=RUNTIME) +
   `@Target` 값 포함 확인 + 속성값 왕복 확인을 **한 테스트**에서.
