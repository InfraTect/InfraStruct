# InfraStruct 코딩 컨벤션

이 문서는 **"규칙이 무엇인가"** 를 한곳에 모은 참조 문서다.

> - "**왜** 이렇게 정했나" → [`plan.md`](./plan.md)
> - "**어떻게** 작업하나(세팅·명령어·PR)" → [`CONTRIBUTING.md`](./CONTRIBUTING.md)
> - 규칙을 어겼을 때의 대처 → `CONTRIBUTING.md` §6 트러블슈팅

---

## 1. 누가 무엇을 강제하나 — 3층 구조

규칙이 서로 겹치지 않도록 세 층이 **역할을 나눠** 맡는다.

| 층 | 파일 | 담당 | 어겼을 때 |
|---|---|---|---|
| ① 에디터 | `.editorconfig`, `.gitattributes` | 인코딩·개행·기본 들여쓰기 | 강제 아님 (IDE 가 미리 맞춰줌) |
| ② 포맷터 | Spotless + google-java-format(AOSP) | 공백·줄바꿈·import 정렬 | `spotlessCheck` 실패 → **`spotlessApply` 로 자동 수정** |
| ③ 린터 | Checkstyle (`config/checkstyle/checkstyle.xml`) | 이름·구조·관례 | `checkstyleMain` 실패 → **직접 수정** |

핵심은 **②와 ③이 절대 겹치지 않는다**는 것이다. Checkstyle 룰셋에는 들여쓰기·공백 규칙이 하나도 없다.
넣으면 두 도구가 서로 다른 답을 요구해서 양쪽을 동시에 통과할 수 없는 코드가 생긴다.
(같은 이유로 `google_checks.xml` 은 쓰지 않는다 — 2-space 를 강제해 AOSP 4-space 와 충돌한다.)

---

## 2. 포맷팅 — 사람이 판단하지 않는다

`framework/build.gradle` 의 `googleJavaFormat('1.22.0').aosp()` 한 줄이 아래를 전부 결정한다.

| 항목 | 값 |
|---|---|
| 들여쓰기 (블록) | **4-space** (탭 금지) |
| 들여쓰기 (연속 — 아래 설명) | 8-space |
| 최대 줄 길이 | **100 컬럼** (넘으면 포맷터가 접는다) |
| 중괄호 | K&R — `{` 는 줄 끝, `else` 는 `}` 와 같은 줄 |
| 후행 공백 / 파일 끝 개행 | 제거 / 항상 추가 |
| Javadoc | 재포맷 대상 (`<p>` 위치까지 손댄다) |

**스타일을 두고 논쟁하지 않는 것이 이 구성의 목적이다.** 손으로 맞추지 말고 `./gradlew spotlessApply` 를 실행한다.

### 블록 들여쓰기 vs 연속 들여쓰기

들여쓰기에는 성격이 다른 두 종류가 있다.

- **블록(4칸)** — `{` 안으로 들어갈 때. 중첩이 한 단계 깊어진 것.
- **연속(8칸)** — 문장이 안 끝났는데 100 컬럼을 넘겨 **줄만 바뀐 것**. 중첩이 깊어진 게 아니다.

실제 포맷 결과:

```java
class IndentProbe {
    private static ResourceGraphStub buildGraph(           // 4칸  — 블록(클래스 안)
            List<String> nodes, String store, int depth) { // 12칸 — 연속(4+8)
        String message =                                   // 8칸  — 블록(메서드 본문)
                "resource " + nodes + " failed to apply";  // 16칸 — 연속(8+8)
        if (nodes.isEmpty()) {
            return null;                                   // 12칸 — 블록(8+4)
        }
    }
}
```

**왜 4 가 아니라 8 인가.** 연속도 4칸이면 접힌 파라미터가 `4+4=8` 칸에 놓여 **메서드 본문과 같은 열**이 된다
→ 시그니처가 어디서 끝나고 본문이 어디서 시작하는지 눈으로 구분할 수 없다.
연속을 블록의 2배로 두면 "이 줄은 더 깊이 중첩된 게 아니라 윗줄의 연장"이라는 게 한눈에 보인다.
(Google 2-space 스타일도 블록 2 / 연속 4 로 같은 1:2 비율을 쓴다.)

> 이 규칙은 **외울 필요가 없다.** `spotlessApply` 가 100% 자동 처리한다.
> "왜 내 코드가 저렇게 접혔지?" 싶을 때 답을 찾으라고 적어둔 것이다.

한 가지 예외가 Checkstyle 에도 있다: 포맷터는 긴 URL·문자열을 접지 못하므로,
`LineLength` 상한을 **120** 으로 두되 `package`/`import` 줄과 URL 이 든 줄은 검사에서 뺀다.
즉 **평상시 100, 도저히 안 되면 120 까지** 허용된다.

- 인코딩 **UTF-8**, 줄바꿈 **LF** (`.editorconfig` · `.gitattributes` 가 강제)
- 예외: `*.bat` 은 CRLF 유지, `*.md` 는 후행 공백 유지(마크다운에서 공백 2개가 줄바꿈이라)

> 들여쓰기가 4-space 인 이유는 `plan.md` §3 참조. MVP-2 에서 계승한 `.editorconfig` 가 이미 4-space 였고,
> 거기에 맞는 포맷터 변형(AOSP)을 고른 것이다. AOSP 역시 google-java-format 이 1급 지원하는 공식 변형이다.

---

## 3. 네이밍 — Checkstyle 이 정규식으로 강제

| 대상 | 규칙 | 예 |
|---|---|---|
| 클래스 / 인터페이스 / enum / record | `UpperCamelCase` | `ResourceGraph` |
| 메서드 | `lowerCamelCase` | `applyAll()` |
| 인스턴스 필드 | `lowerCamelCase` | `stateStore` |
| `static final` 상수 | `UPPER_SNAKE_CASE` | `DEFAULT_TIMEOUT` |
| `static` 비상수 필드 | `lowerCamelCase` | `instanceCount` |
| 파라미터 / 지역변수 / 람다 파라미터 | `lowerCamelCase` | `resourceId` |
| record 컴포넌트 / 패턴 변수 | `lowerCamelCase` | `case Ec2 ec2 ->` |
| 패키지 | 소문자만 (언더스코어·대문자 금지) | `com.infrastruct.internal` |
| 제네릭 타입 파라미터 | **대문자 한 글자** | `<T>`, `<R>` |

⚠️ **제네릭 타입 파라미터는 현재 한 글자만 통과한다** (`^[A-Z]$`). `<T2>` 나 `<RESOURCE>` 는 실패한다.
`spi` 계약을 설계하다 두 글자 이상이 필요해지면, `checkstyle.xml` 의 `ClassTypeParameterName` /
`MethodTypeParameterName` / `InterfaceTypeParameterName` 에 `format` 을 `^[A-Z][0-9]?$` 정도로 완화한다.

> 소스 패키지는 `com.infrastruct`, 배포 좌표 group 은 `com.infratect` 로 서로 다르다 (**struct** vs **tect**).
> 오타가 아니라 현재 상태이며, groupId 확정 시점(`plan.md` §9)에 함께 정리할 항목이다.

---

## 4. import

- **스타 임포트 금지** — `import java.util.*` 는 Checkstyle 이 막는다. 어떤 타입이 어디서 왔는지 추적 가능해야 한다.
- **안 쓰는 import 는 자동 삭제**된다 (Spotless `removeUnusedImports()`). 중복 import 도 금지.
- **`sun.*` 등 JDK 내부 API 직접 import 금지.**
- **순서는 포맷터가 자동으로 맞춘다** — `static` import 블록 → 빈 줄 → 일반 import 블록, 각 블록 내부는 ASCII 정렬.

```java
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
```

---

## 5. 코드 구조 관례 (Checkstyle, 자동 수정 안 됨)

**흔한 버그를 막는 것**

- `if`·`for`·`while` 은 **한 줄짜리라도 중괄호**를 쓴다 — 나중에 줄을 추가하다 생기는 버그 차단.
- **빈 `catch` 금지.** 의도적으로 무시하는 경우라면 예외 변수명을 `ignored` 또는 `expected` 로 둔다.
- 문자열 비교는 `==` 이 아니라 `equals()` — 상수를 앞에 두는 편(`"a".equals(x)`)을 권장.
- `equals()` 를 재정의하면 `hashCode()` 도 함께 재정의한다.
- `switch` 의 fall-through 금지, `default` 는 마지막에.
- 한 줄에 한 문장, 변수 선언도 한 줄에 하나.
- `finalize()` 금지, 반복문 제어 변수를 본문에서 수정 금지.

**구조**

- **파일당 top-level 클래스 하나**, 파일명 = 클래스명.
- `private` 생성자만 있는 클래스는 `final` 로 선언한다.
- `static` 멤버만 있는 유틸 클래스는 `private` 생성자로 인스턴스화를 막는다.
- 제어자는 표준 순서대로 (`public static final ...`), 불필요한 제어자는 쓰지 않는다
  (인터페이스 메서드의 `public`, `final` 필드의 중복 제어자 등).
- 배열은 `String[] args` 형식으로 (`String args[]` 금지).

**어느 패키지에 넣을지**(`api` / `spi` / `internal`)는 `CONTRIBUTING.md` §2 에 정리돼 있다.

---

## 6. Javadoc

### 6.1 Javadoc 이란 — 도구가 읽는 주석

Java 의 주석은 세 가지이고, **별이 두 개인 `/**` 만 특별 취급**된다.

```java
// 한 줄 주석 — 사람만 읽는다
/* 여러 줄 주석 — 사람만 읽는다 */
/** Javadoc — 도구가 읽는다 */
```

앞의 둘은 컴파일 시 버려지지만, Javadoc 은 두 곳으로 흘러간다.

1. **IDE 호버** — 메서드에 마우스를 올렸을 때 뜨는 설명창.
2. **HTML API 문서** — `./gradlew javadoc` 이 생성한다. Oracle 의 Java API 문서가 이 방식으로 만들어진 것이다.

> **이 프로젝트에서 특히 중요한 이유**: InfraStruct 는 라이브러리다.
> 프로바이더 개발자는 우리 소스를 보지 않고 **jar 만 받아 IDE 호버로 읽는다.** 그때 보이는 것이 Javadoc 뿐이다.
> `spi` 의 Javadoc 은 사실상 프로바이더 개발자를 위한 유일한 설명서다.

### 6.2 어디에 붙이나 — 선언 바로 위

```java
/** 여기가 Javadoc. */
public class ResourceGraph {

    /** 필드에도 붙는다. */
    private final String id;

    /** 메서드에도. */
    public void apply() {}
}
```

- 선언과 Javadoc 사이에 빈 줄이나 다른 코드가 끼면 안 된다.
- **어노테이션은 Javadoc 아래**에 온다.

```java
/** 설명. */
@Override
public void apply() {}
```

- **패키지 자체**에 문서를 다는 전용 파일이 `package-info.java` 다. 이 레포의 `api`/`spi`/`internal` 이 이미 이 형태다.

### 6.3 구조 — 요약 / 본문 / 태그

```java
/**
 * 리소스 그래프를 위상 정렬해 적용 순서를 계산한다.          ← ① 요약 (첫 문장)
 *
 * <p>의존 관계에 사이클이 있으면 적용을 시작하지 않는다.      ← ② 본문
 * 프로바이더는 이 순서를 신뢰해도 된다.
 *
 * @param graph 정렬 대상 그래프                            ← ③ 태그
 * @return 적용 순서대로 정렬된 리소스 목록
 * @throws CyclicDependencyException 그래프에 사이클이 있을 때
 */
```

**① 요약 문장이 가장 중요하다.** 첫 마침표까지가 잘려나가 클래스 목록·메서드 목록에 **한 줄 요약으로 표시**된다.
짧고 완결적으로 쓰고, 마침표를 빠뜨리지 않는다(빠뜨리면 문단 전체가 요약으로 들어간다).

**② 본문의 `<p>`** — Javadoc 은 HTML 로 렌더링되므로 빈 줄만으로는 문단이 나뉘지 않는다.
새 문단 **시작**에 `<p>` 를 붙인다(닫는 `</p>` 는 쓰지 않는다). `<ul><li>`, `<b>` 도 쓸 수 있다.

### 6.4 태그

**블록 태그** — 줄 맨 앞에 쓰며, `@param` → `@return` → `@throws` 순서.

| 태그 | 용도 |
|---|---|
| `@param 이름 설명` | 파라미터마다 하나씩 |
| `@return 설명` | 반환값 (`void` 면 생략) |
| `@throws 예외 설명` | **어떤 조건에서** 던지는지 |
| `@deprecated 설명` | 폐기 예정 + 대체재 안내 |
| `@since 0.1.0` | 어느 버전부터 존재하는지 |
| `@see 대상` | 관련 항목 참조 |

**인라인 태그** — 문장 속에서 중괄호로.

| 태그 | 용도 |
|---|---|
| `{@code List<String>}` | 코드 서식 + **HTML 해석 방지** |
| `{@link ResourceGraph#apply()}` | 다른 클래스·메서드로 링크 |

⚠️ **`{@code}` 는 사실상 필수다.** Javadoc 은 HTML 이라 `<`, `>` 를 그냥 쓰면 태그로 먹혀 문서가 깨진다.

```java
 * ❌ List<String> 을 반환한다.              → <String> 이 HTML 태그로 먹혀 사라진다
 * ✅ {@code List<String>} 을 반환한다.
```

### 6.5 무엇을 쓸 것인가

**코드를 읽으면 아는 내용을 반복하는 Javadoc 은 쓰지 않느니만 못하다.** 유지보수 부담만 늘어난다.

```java
/**
 * 이름을 반환한다.      ← ❌ 메서드 이름의 반복. 정보량 0.
 * @return 이름
 */
public String getName() { ... }
```

대신 **코드에 드러나지 않는 것**을 쓴다.

- `null` 이 될 수 있는가, 언제 그런가
- 호출 순서 제약 (이것보다 먼저 불러야 하는 것)
- 부수효과, 스레드 안전성
- 값의 유효 범위·단위
- **왜** 그렇게 되어 있는가

```java
/**
 * 리소스의 고유 식별자. 프로바이더가 생성하기 전에는 {@code null} 이다.
 *
 * <p>apply 이후에는 절대 바뀌지 않는다 — 상태 비교의 기준이 되기 때문.
 */
```

### 6.6 이 프로젝트의 규칙

- **한국어로 쓴다.** (기존 소스가 모두 그렇다)
- **작성 자체는 아직 의무가 아니다.** 현재 룰셋은 "쓰라"가 아니라 **"쓴 것이 틀리지 않았는지"** 만 본다.
  - `@param`·`@throws` 가 실제 시그니처와 맞는지 (이름 오타 시 실패)
  - `@throws` 에 적은 예외가 실제로 던져지는지 (`validateThrows = true`)
  - 태그 설명이 비어 있지 않은지, Javadoc 이 엉뚱한 위치에 붙지 않았는지
- **포맷은 손대지 않는다.** `spotlessApply` 가 Javadoc 도 재포맷해 `<p>` 위치까지 정리한다.
- 문서 생성은 `./gradlew javadoc` → `framework/build/docs/javadoc/index.html`.
  (스캐폴드 단계에서는 실패한다 — `CONTRIBUTING.md` §6 참조.)

> 공개 API 가 실제로 생기면 `api`·`spi` 한정으로 작성 의무(`MissingJavadocType` / `MissingJavadocMethod`)를 켜는 것이 다음 수순이다.
> `checkstyle.xml` 에 해당 모듈이 주석으로 남아 있으니 주석만 풀면 된다.

---

## 7. 일반 주석 · 기타 관례

도구가 강제하지 않지만 지켜온 것들.

- **주석은 한국어로 쓴다.** (모든 기존 소스·빌드 스크립트가 일관되게 그렇다)
- 주석은 "무엇"보다 **"왜"** 를 설명한다. 코드를 읽으면 아는 내용은 적지 않는다.
  - 예: `gradle.properties` — *"org.gradle.java.home 을 여기 하드코딩하지 말 것 — OS/머신이 바뀌면 깨진다"*
- 패키지마다 `package-info.java` 로 역할과 안정성 등급을 명시한다.
- 커밋 메시지는 `feat:` / `docs:` / `fix:` 접두를 붙인다 (Conventional Commits).

---

## 8. 규칙을 바꾸고 싶을 때

규칙이 실제 코드와 맞지 않는다고 판단되면, **좁은 예외부터** 시도한다.

| 범위 | 방법 |
|---|---|
| 한 곳만 | 코드에 `@SuppressWarnings("checkstyle:RuleName")` |
| 특정 파일·경로 | `config/checkstyle/suppressions.xml` 에 항목 추가 (**이유를 주석으로 남길 것**) |
| 규칙 자체 | `config/checkstyle/checkstyle.xml` 수정 + PR 에서 이유 설명 + `plan.md` §8 에 기록 |

현재 예외로 등록된 것:

- **테스트 소스** — `HideUtilityClassConstructor`, `FinalClass`, `JavadocMethod` 면제.
  픽스처·헬퍼 클래스가 많아 설계 규칙을 그대로 적용하기 어렵다.

> 규칙을 통째로 끄기 전에 항상 물어볼 것: **"이 규칙이 틀린 건가, 아니면 코드가 틀린 건가?"**
