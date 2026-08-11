# spec: infrastructapplication-annotation

`plan.md` 를 테스트 가능한 **행동 목록**으로 옮긴 것. 아래 `- ` 불릿들이 첫 `/red`
때 체크리스트(`behaviors[]`)로 등록되고, 위에서부터 순서대로 red→green 을 돈다.

## 행동 목록 (red 사이클 순서)

### `@InfraStructApplication` (완전 구현)

- @InfraStructApplication 이 RUNTIME 까지 유지되어 리플렉션으로 읽을 수 있다
- @InfraStructApplication 을 타입(클래스)에 붙일 수 있다
- provider() 는 선언한 문자열 값을 그대로 반환한다
- provider() 에는 기본값이 없다 (필수 속성이다)

### `InfraStruct` (뼈대만 — 본문 빈 스텁)

- InfraStruct 는 provider 문자열을 받는 생성자로 인스턴스를 만들 수 있다
- InfraStruct 의 인스턴스 run() 은 빈 스텁이라 호출해도 예외 없이 반환한다
- InfraStruct 의 static run(Class) 는 빈 스텁이라 호출해도 예외 없이 반환한다

## 공개 인터페이스 시그니처 (확정)

### `com.infrastruct.api.InfraStructApplication`

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface InfraStructApplication {
    String provider();   // 기본값 없음 → 필수
}
```

### `com.infrastruct.api.InfraStruct`

```java
public class InfraStruct {
    // 모듈 필드 7개(resourceScanner ... applier)는 타입이 아직 없어 이번엔 선언하지 않음.

    public InfraStruct(String provider) {
        // 본문 비움. 원래: ModuleRegistry 가 provider 로 Validator/Applier 등을 찾아 필드에 주입.
    }

    public static void run(Class<?> mainClass) {
        // 본문 비움. 원래: mainClass 의 @InfraStructApplication 을 읽어
        // provider 문자열 추출 → new InfraStruct(provider) → run().
    }

    public void run() {
        // 본문 비움. 원래: scan → desired → validate → load → compare → plan → apply → save 파이프라인.
    }
}
```

## 검증 메모 (어떻게 테스트할지)

> 주의: 이 절은 **행동이 아니라 구현 힌트**다. 하네스가 `- ` 불릿을 행동으로 자동
> 등록하므로, 여기서는 일부러 `- ` 대신 번호 목록을 쓴다.

1. RUNTIME: 픽스처 클래스에 어노테이션을 붙이고
   `fixture.getAnnotation(InfraStructApplication.class)` 가 null 이 아님을 확인.
2. TYPE: `InfraStructApplication.class` 의 `@Target` 값에 `ElementType.TYPE` 포함 확인.
3. provider() 값: `provider="aws"` 로 붙인 픽스처에서 읽어 `"aws"` 와 같은지 확인.
4. 기본값 없음: `InfraStructApplication.class.getDeclaredMethod("provider").getDefaultValue()`
   가 null 인지 확인.
5. InfraStruct 스텁: `new InfraStruct("aws")`, `instance.run()`, `InfraStruct.run(X.class)`
   가 예외를 던지지 않고 반환하는지 확인 (본문이 비어 있으므로).
