# summary: module-registry

브랜치: `feat/module-registry-impl`

`@InfraStructApplication(provider = "aws")` 의 **문자열 하나**를 그 프로바이더의 **Validator·Applier
객체**로 바꿔 주는 연결을 완성했다. 이번 feature 는 앞의 것들과 달리 스텁이 아니라 **실제 동작**을 채운다.

## 무엇을 만들었나

| 결과물 | 상태 | 파일 |
|---|---|---|
| `ModuleRegistry` | **완전 구현** | `framework/src/main/java/com/infrastruct/internal/ModuleRegistry.java` |
| `ModuleRegistryException` | **완전 구현** | `framework/src/main/java/com/infrastruct/internal/ModuleRegistryException.java` |
| `RegisterProvider.validator()` | 상한을 `Class<? extends Validator>` 로 좁힘 | `framework/src/main/java/com/infrastruct/spi/RegisterProvider.java` |
| `InfraStruct` | 생성자 주입 + `run(Class)` 배선 (파이프라인 본문은 여전히 TODO) | `framework/src/main/java/com/infrastruct/api/InfraStruct.java` |

테스트: `ModuleRegistryTest`(12), `InfraStructTest`(6), `RegisterProviderTest`(3, 1개 추가).
픽스처: `framework/src/test/java/com/infrastruct/fixture/provider/` 아래 19개
(정상 토큰 alpha·beta, 미등록 토큰, 그리고 `twin`/`rogue`/`abstract-validator`/`no-ctor`/
`private-ctor`/`interface-applier`/`throwing` 로 **id 격리된** 깨진 설정들).

## 확정된 계약

```java
public final class ModuleRegistry {
    public ModuleRegistry(String providerId);   // 스캔 + 선택 + 검증
    public Validator validator();               // 매번 새 인스턴스
    public Applier   applier();                 // 매번 새 인스턴스
    Class<? extends Provider> providerToken();  // 선택 결과 (package-private)
}
```

동작 흐름:

1. `InfraStruct.run(Main.class)` 가 `@InfraStructApplication` 에서 provider 문자열을 읽는다.
2. `new InfraStruct(provider)` → `new ModuleRegistry(provider)`.
3. classpath 에서 `@RegisterProvider` 토큰을 전수 스캔(classgraph)해 `providerId` 로 하나를 고른다.
4. 토큰이 등록한 세 `Class` 를 필드에 보관한다 — **필드는 `Class`, 접근자가 인스턴스**(다이어그램 그대로).
5. `validator()`/`applier()` 가 `public 무인자 생성자`로 매번 새 객체를 만든다.

접근자 이름은 다이어그램의 `getValidator()` 대신 **`validator()`** 다 — 레포의 기존 접근자
(`ResourceScanner.basePackage()`)가 `get` 접두를 쓰지 않기 때문. 계약(무엇을 돌려주는가)은 그대로다.

## 왜 이렇게 했나 (핵심)

- **스캔·선택은 생성자, 객체화는 접근자.** 설정 오류는 파이프라인을 돌기 전에 세우고, 객체 생성 비용은
  실제로 쓸 때 낸다. `new ModuleRegistry("abstract-validator")` 는 성공하고 `validator()` 에서 터진다.
- **캐싱하지 않는다.** 인스턴스도, 스캔 결과도 필드/static 에 담지 않는다. 전역 상태는 테스트 격리를 깬다.
- **검증은 요청한 id 에 대해서만.** 남의 프로바이더가 깨져 있다고 내 실행이 막히지 않는다. 이 설계 덕분에
  깨진 픽스처들을 같은 test classpath 에 올려 두고도 서로 오염시키지 않는다(`spec.md` §5.2).
- **스캔 기준은 상속이 아니라 어노테이션.** `Provider` 를 상속했지만 `@RegisterProvider` 가 없는 클래스는
  발견되지 않고, 반대로 어노테이션만 있고 상속하지 않은 토큰은 전용 메시지로 실패한다.
- **예외 메시지에 고칠 대상을 담는다**(`CONVENTIONS.md` §8.3). 아래 표가 그 계약이고, 테스트가 고정한다.

| 실패 | 메시지에 담기는 것 |
|---|---|
| providerId 가 비었음(`null`·`""`·공백) | `@InfraStructApplication` |
| 미발견 | 찾던 id + 발견된 id 목록(중복 제거·사전순) |
| 같은 id 중복 | 충돌한 토큰 FQCN 전부 + 그 id |
| 토큰이 `Provider` 미상속 | 토큰 FQCN + `Provider` FQCN |
| 인스턴스화 불가 | 구현체 FQCN + `public 무인자 생성자` 요구 |
| 구현체 생성자가 던짐 | 구현체 FQCN + **cause 에 진짜 원인**(`InvocationTargetException` 을 벗김) |
| 메인 클래스에 어노테이션 없음 | 메인 클래스 FQCN + `@InfraStructApplication` |

## 다음(이 브랜치 밖에서 이어질 일)

- `InfraStruct` 의 나머지 모듈 5개 필드와 `run()` 파이프라인 본문
  (scan → desired → validate → load → compare → plan → apply → save).
- 경계에서의 사용자용 메시지 변환(`CONVENTIONS.md` §8.5) — 지금은 `ModuleRegistryException` 을 그대로 올린다.
- 실제 프로바이더 구현체(`Aws`, `AwsValidator`, `AwsApplier`)는 프로바이더 레포 몫.

## TDD 진행 메모

- 행동 14개를 선언한 순서 그대로 red→green 14 사이클을 돌았다. 순서는 "각 사이클에서 아직 없는 코드가
  하나씩 생기도록" 잡았다(`spec.md` §3) — 예: 행동 10 을 `catch (ReflectiveOperationException)` 한
  덩어리로 구현해야 행동 11(cause 언래핑)이 RED 가 된다.
- 사이클 3·12 는 RED 단계에서 기존 테스트를 함께 고쳤다. green 단계에서는 게이트가 `src/test` 편집을
  막기 때문이다. `InfraStructTest` 는 스텁 시절 3개를 6개로 교체했다(`spec.md` §7).
- `@RegisterProvider.validator()` 가 `Validator` 아닌 타입을 가리키는 경우는 Java 소스로 재현할 수
  없어(상한이 컴파일을 막는다) 행동 목록에서 뺐다. 옛 코어로 컴파일된 jar 대비 **방어 코드는 남겼다**
  (`ModuleRegistry.narrow`).
- QA 에서 걸린 것 두 가지: Checkstyle 이 private 메서드 Javadoc 에도 `@param`/`@return`/`@throws` 를
  요구했고, SpotBugs 가 `ThrowingValidator` 픽스처의 "생성자에서 예외"를 `CT_CONSTRUCTOR_THROW` 로
  잡았다(→ 클래스를 `final` 로 선언해 해결).
