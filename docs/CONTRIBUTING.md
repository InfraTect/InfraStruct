# InfraStruct 기여 가이드

이 문서는 **InfraStruct 코어(`framework`)를 개발하는 사람**을 위한 실무 안내서다.

> "**왜** 이렇게 설계했나"는 [`plan.md`](./plan.md) 에 있다. 이 문서는 "**어떻게** 작업하나"에 집중한다.
> 두 문서가 겹치지 않도록, 설계 배경이 필요하면 이 문서는 `plan.md` 의 해당 절로 링크만 건다.

---

## 1. 시작 전 세팅

### 1.1 JDK 21 (필수) ⚠️

**이 프로젝트는 JDK 21 에서만 빌드된다.**

- Gradle 8.10.2 는 JDK 23 까지만 지원한다 → IntelliJ 내장 JBR(25)로는 빌드가 깨진다.
- `framework/build.gradle` 의 toolchain 이 **컴파일**은 21로 강제하지만, **Gradle 자체를 띄우는 JVM**도 21이어야 한다.

설치 (택 1):

```bash
# Homebrew
brew install openjdk@21

# 또는 SDKMAN
sdk install java 21-tem
```

배경: `plan.md` §7.

### 1.2 터미널에서 Gradle 돌리기

macOS + Homebrew 로 깔았다면 `JAVA_HOME` 을 지정해야 한다. `~/.zshrc` 에 추가:

```bash
export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
```

새 터미널을 열고 확인:

```bash
java -version   # openjdk version "21.x.x" 가 나와야 함
```

### 1.3 IntelliJ 설정

`Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JVM` 을 **21** 로.
(목록에 없으면 `Download JDK → Temurin 21`)

> ❌ `org.gradle.java.home` 을 `gradle.properties` 에 **하드코딩하지 말 것** — 머신마다 경로가 달라 남의 빌드가 깨진다.

### 1.4 세팅 확인

```bash
./gradlew build
```

`BUILD SUCCESSFUL` 이 뜨면 준비 끝.

---

## 2. 프로젝트 구조 (작업 폴더)

```
InfraStruct/
├── settings.gradle           # 모듈 선언(framework) + toolchain resolver
├── build.gradle              # 루트 빌드 스크립트 (거의 빔 — 단일 모듈이라)
├── gradle/  gradlew  ...      # Gradle wrapper (8.10.2)
├── docs/
│   ├── plan.md               # 설계·결정 로그 ("왜")
│   └── CONTRIBUTING.md        # 이 문서 ("어떻게")
├── .github/workflows/ci.yml  # CI (PR 검사)
└── framework/                # ★ 실제 코드가 사는 모듈
    ├── build.gradle          # Java 21 + Spotless + SpotBugs + 테스트
    └── src/
        ├── main/java/com/infrastruct/
        │   ├── api/          # 사용자가 import 하는 공개 API
        │   ├── spi/          # 프로바이더가 구현/상속 (가장 안정적이어야)
        │   └── internal/     # 엔진 내부 (외부 노출 금지)
        └── test/java/com/infrastruct/
```

**대부분의 작업은 `framework/src/` 안에서 이뤄진다.** 루트의 빌드 파일들은 세팅이 끝나면 거의 건드리지 않는다.

### 어디에 클래스를 넣나 — api / spi / internal

패키지는 "기술 종류"가 아니라 **"바꾸면 누가 깨지나"** 로 나눈다.

| 패키지 | 누가 의존 | 바꾸면 | 안정성 |
|---|---|---|---|
| `com.infrastruct.api` | 사용자(개발자) | 사용자 코드가 깨짐 | 신중 |
| `com.infrastruct.spi` | 프로바이더 | **모든 프로바이더가 깨짐** | **최우선 안정** |
| `com.infrastruct.internal` | 엔진만 | 아무도 안 깨짐 | 자유롭게 리팩터링 |

**판별 규칙**: "엔진 밖의 누군가가 이 타입을 코드에서 직접 쥐는가?"
사용자가 쥐면 → `api` · 프로바이더가 쥐면 → `spi` · 아무도 안 쥐면 → `internal`.

자세히: `plan.md` §5.

---

## 3. 검사 명령어 (매일 쓰는 것)

모두 **프로젝트 루트**에서 실행한다 (JDK 21 필요).

| 목적 | 명령어 | 결과 / 리포트 |
|---|---|---|
| 포맷 자동 수정 | `./gradlew spotlessApply` | 코드를 규칙대로 정렬 |
| 포맷 검사만 | `./gradlew spotlessCheck` | 어긋나면 실패 |
| 린터 (버그 탐지) | `./gradlew spotbugsMain` | `framework/build/reports/spotbugs/main.html` |
| 테스트 | `./gradlew test` | 커버리지: `framework/build/reports/jacoco/test/html/index.html` |
| **전체** | `./gradlew build` | 위 전부 포함 (컴파일 + 테스트 + 포맷 + 린터 + jar) |

> **커밋 전 습관**: `./gradlew spotlessApply` 로 포맷을 맞추고 → `./gradlew build` 로 전체 그린을 확인한다.
> `build` 하나가 CI 가 도는 검사(포맷·테스트·린터)를 전부 포함하므로, 로컬에서 `build` 가 그린이면 CI 도 대개 그린이다.

---

## 4. 코드 컨벤션

- **포맷은 Spotless 가 강제한다.** 손으로 맞추지 말고 `./gradlew spotlessApply` 를 실행한다.
  - google-java-format 의 AOSP 변형 = **들여쓰기 4-space**.
  - 안 쓰는 import 는 자동 삭제된다.
- 인코딩 **UTF-8**, 줄바꿈 **LF** (`.editorconfig` · `.gitattributes` 가 강제).
- 커밋 전 반드시 `spotlessApply` — 안 하면 CI 의 `spotlessCheck` 에서 막힌다.

---

## 5. 브랜치 & PR 규칙

**브랜치 모델**
- `main` — 배포 브랜치.
- `dev` — 개발 통합 브랜치.
- **기능 개발**: `dev` 에서 브랜치를 파서 작업 → `dev` 로 PR.
- **긴급 패치(hotfix)**: `main` 에서 브랜치를 파서 작업 → `main` 으로 PR.
- `dev`·`main` 직접 푸시 금지 — 병합은 **PR 필수**.

**작업 흐름 예 (기능 개발)**
```bash
git checkout dev
git pull
git checkout -b feature/무슨-기능
# ... 작업 ...
./gradlew spotlessApply && ./gradlew build   # 올리기 전 로컬 검증
git push -u origin feature/무슨-기능
# GitHub 에서 dev 로 PR 생성
```

PR 을 올리면 **CI(`build-test-format-lint`)가 자동 실행**된다. 빌드 + 테스트(JUnit) + 포맷(Spotless) + 린터(SpotBugs) 를 모두 통과해야 병합할 수 있다.

자세한 전략: `plan.md` §10.

---

## 6. 트러블슈팅

**빌드가 깨진다 / "Unsupported class file major version" 류 에러**
→ Gradle 이 JDK 21 이 아닌 JVM 으로 돌고 있다. §1 로 돌아가 확인:
- 터미널: `java -version`, `echo $JAVA_HOME`
- IntelliJ: `Gradle JVM` 설정

**`spotlessCheck` 가 실패한다**
→ 포맷이 어긋났다. `./gradlew spotlessApply` 를 실행하고 다시 커밋한다.

**`spotbugsMain` 이 `NO-SOURCE` 라고 나온다**
→ 정상이다. 아직 분석할 실제 클래스가 없어서다(스캐폴드 단계). 코드를 작성하면 자동으로 분석한다.
