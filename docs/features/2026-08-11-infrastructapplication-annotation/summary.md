# summary: infrastructapplication-annotation

브랜치: `feat/InfraStructApplication-annotation-impl`

## 무엇을 만들었나

| 결과물 | 상태 | 파일 |
|---|---|---|
| `@InfraStructApplication` | **완전 구현** | `framework/src/main/java/com/infrastruct/api/InfraStructApplication.java` |
| `InfraStruct` | **뼈대(스텁)** — 공개 시그니처 3개, 본문 비움 | `framework/src/main/java/com/infrastruct/api/InfraStruct.java` |

테스트: `framework/src/test/java/com/infrastruct/api/`
`InfraStructApplicationTest`(4), `InfraStructTest`(3).

## 확정된 계약

```java
// 사용자의 메인 클래스에 붙인다.
@InfraStructApplication(provider = "aws")
public class MyApp { ... }
```

- `@InfraStructApplication` : `String provider()` (필수, 기본값 없음),
  `@Retention(RUNTIME)`, `@Target(TYPE)`.
- `InfraStruct` : `static void run(Class<?>)`, `InfraStruct(String)`, `void run()`.

## 왜 이렇게 했나 (핵심)

- **provider 는 String** — 코어가 클라우드 목록을 몰라야 하기 때문. 런타임에 이 문자열로
  프로바이더를 탐색한다. (`docs/plan.md` §22·26·144·187)
- **어노테이션은 RUNTIME 리텐션** — 컴파일 후 리플렉션으로 읽어야 provider 를 알 수 있다.
- **InfraStruct 본문은 비움** — 생성자·`run()` 이 쓸 모듈들과 이들을 주입할
  `ModuleRegistry` 가 아직 없다(다른 브랜치). 채울 자리는 각 메서드에 `TODO` 주석으로 표시.

## 다음(이 브랜치 밖에서 이어질 일)

- `ModuleRegistry` 와 모듈 타입들이 생기면 `InfraStruct` 의 필드 7개와 생성자/`run()`
  본문을 채운다.
- `run(Class<?>)` 안의 "어노테이션 읽기 → provider 추출 → `new InfraStruct(...)` → `run()`"
  연결을 구현한다.

## TDD 진행 메모

- 행동 #3(provider 값 반환)·#4(기본값 없음)는 #1 구현 시 `provider` 속성이 생기며 이미
  충족되어 별도 RED 를 만들 수 없었다. → 회귀 테스트로 검증만 하고 체크리스트에서 done 처리.
- spec.md 의 "검증 메모"가 `- ` 불릿이라 하네스가 행동으로 오등록 → 번호 목록으로 바꿔 정리.
