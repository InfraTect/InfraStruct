# InfraStruct 프로젝트 기본 설정 Plan

> 목적: 이 레포를 **배포 가능한 Java 프레임워크 "코어"** 로 세팅한다.
> InfraStruct는 "뼈대(framework)"이고, 각 클라우드 프로바이더가 이 위에 자신의 자원을
> **코어 수정 없이** 추가/배포할 수 있어야 한다.
>
> **핵심 모델(변경됨): 멀티레포.** Terraform처럼 — 코어(이 레포)와 각 프로바이더 레포가 분리된다.
> 이 레포에는 프로바이더 구현체·example이 들어오지 않는다. 그것들은 각각 별도 레포에서
> **배포된 framework 아티팩트**에 의존한다.
>
> 상태 범례: ✅ 확정/완료 · 🔶 결정 대기 · 💡 tier-2(첫 릴리스 이후)

---

## 0. 배경 — 참고한 것

옆 디렉토리의 두 MVP(`InfraStruct-MVP-2`, `InstraStruct-MVP`)를 조사해서 계승/개선 지점 정리.

**MVP-2에서 계승한 것**
- Java 21 toolchain, Gradle 8.10.2 wrapper
- `java-library` 플러그인, JUnit 5(BOM), UTF-8/LF, `.editorconfig`, `.gitattributes`, `.gitignore`
- 프로그래밍 모델: `@InfraStructApplication(provider="aws")` + `InfraStruct.run(...)` + `@Resource extends AwsEc2` (제어의 역전 = 프레임워크)
- 코어는 클라우드를 모른다 (프로바이더 → framework 단방향 의존)

**개선한 것**
- `provider`를 **enum → String** 으로 변경 → 코어에서 프로바이더 목록이 사라짐(코어가 클라우드를 완전히 모르게 됨)
- **멀티모노레포 → 멀티레포** 로 전환 (아래 §2)
- 포맷터/린터/테스트 보강 (아래 §3, 이번에 완료)

---

## 1. 확정 스택 요약

| 항목 | 결정 | 상태 |
|---|---|---|
| Java 버전 | **21 LTS** (toolchain), `release=21` 고정 | ✅ |
| 빌드 도구 | **Gradle 8.10.2** (wrapper, MVP-2 계승) | ✅ |
| 빌드 스크립트 DSL | **Groovy DSL** (`.gradle`) | ✅ |
| 레포 모델 | **멀티레포** — 이 레포=코어, 프로바이더/example=별도 레포 | ✅ |
| 모듈 구조 | 이 레포는 **`framework` 단일 모듈** | ✅ |
| 포맷터 | **Spotless + google-java-format (AOSP 4-space)** | ✅ 완료 |
| 린터 | **SpotBugs**(버그 패턴) + **Checkstyle**(코드 관례) | ✅ 완료 |
| 테스트 | JUnit 5 + **AssertJ** + Mockito + **JaCoCo** | ✅ 완료 |
| CI | **GitHub Actions** — main/dev PR·push 에서 빌드·테스트·포맷·린터 검증 | ✅ 완료 |
| 좌표(coordinate) | `com.infratect : infrastruct-framework : 0.1.0` | ✅ (배포는 나중) |
| 배포 | 골격/설정 전부 **나중** (계정 생길 때) | 🔶 §6 |
| 버전 카탈로그 | 지금 불필요(단일 모듈), 나중에 도입 가능 | 💡 |
| build-logic convention plugin | **불필요**(단일 모듈) → 제거 | ✅ |
| 프로바이더 개발 키트(Gradle 플러그인) | 나중에 별도 배포 | 💡 |
| API 호환성 검사(japicmp), NullAway | 첫 릴리스 이후 | 💡 |

---

## 2. 레포 모델 — 멀티레포 (Terraform 방식)

Terraform: HashiCorp의 코어가 한 레포, `terraform-provider-aws` 등은 **별도 레포**. InfraStruct도 동일.

```
[InfraStruct 레포 = 이거]                 [infrastruct-provider-aws 레포 = 별도/나중]
├── framework/  (spi/api/internal)  ◀───── 배포된 framework 아티팩트에 의존
                                           implementation "com.infratect:infrastruct-framework:x.y.z"
                                    [example 레포 = 별도/나중]
```

- **이 레포에는 프로바이더 구현체·example이 없다.** framework 코어만.
- 프로바이더는 우리 소스가 아니라 **배포된 jar**로 framework를 가져다 쓴다.
- 그래서 "프로바이더가 자원을 추가할 때 프레임워크를 수정할 일이 없다"가 레포 수준에서도 보장됨.

**Terraform ↔ InfraStruct 대응**

| Terraform | InfraStruct(Java) |
|---|---|
| 프로바이더가 레지스트리에 배포 | 프로바이더가 Maven에 별도 아티팩트로 배포 |
| `terraform init` 이 프로바이더 다운로드 | 사용자 `build.gradle`에 의존성 한 줄 → Gradle이 다운로드 |
| Terraform이 프로바이더 인식 | 런타임에 `ServiceLoader`가 프로바이더 자동 발견 |

---

## 3. 이번에 완료한 것 (검증됨) ✅

`./gradlew build`가 JDK 21에서 그린인 것을 확인함.

- **포맷터 — Spotless + google-java-format(AOSP 4-space)**
  - `./gradlew spotlessApply`(자동 수정) / `spotlessCheck`(검증). import 정렬 포함.
  - AOSP 변형 선택 → 들여쓰기 4-space (기존 `.editorconfig` 관례 유지). → §5 결정 완료(B안).
  - 4-space 를 먼저 고르고 AOSP 를 찾은 게 아니라, **MVP-2 에서 계승한 `.editorconfig` 가 이미 4-space 라 거기에 맞는 변형을 고른 것**이다(§0). AOSP 도 google-java-format 이 1급 지원하는 공식 변형이며, Oracle 공식 Java 컨벤션·IntelliJ 기본값도 4-space 다.
- **린터 — SpotBugs**
  - 컴파일된 바이트코드에서 버그 패턴(자원 누수, null 역참조, `==` 오용 등)을 찾음.
  - `./gradlew spotbugsMain` → 리포트 `build/reports/spotbugs/main.html`. `build`/`check` 에 자동 포함.
  - 아직 실제 코드가 없어 현재 `NO-SOURCE`(배선만 검증). 코드 작성 시부터 실제 분석 시작.
- **린터 — Checkstyle**
  - 소스코드에서 스타일/관례 위반(네이밍, 스타 임포트, 중괄호 생략, 유틸 클래스 생성자 등)을 찾음.
  - `./gradlew checkstyleMain` → 리포트 `build/reports/checkstyle/main.html`. `build`/`check` 에 자동 포함.
  - 룰셋은 레포 루트 `config/checkstyle/checkstyle.xml`(예외는 `suppressions.xml`) — 모듈이 늘어도 공유.
  - 포맷(들여쓰기/공백/줄바꿈)은 Spotless 담당이라 룰셋에서 **의도적으로 제외**했다. google_checks 는 2-space 강제라 AOSP 와 충돌해서 안 씀.
  - SpotBugs 와 역할이 다르다: SpotBugs=바이트코드의 '버그', Checkstyle=소스의 '관례'.
- **테스트 — JUnit 5 + AssertJ + Mockito + JaCoCo**
  - `./gradlew test` → 실행 + `jacocoTestReport` 커버리지 생성.
- **패키지 뼈대 — spi / api / internal** (각 `package-info.java`로 역할 명시)
- **CI — GitHub Actions (`.github/workflows/ci.yml`)**
  - main/dev 로의 PR + 그 브랜치 push 에서 `spotlessCheck` + `./gradlew build`(테스트·SpotBugs·Checkstyle 포함) 실행. JDK 21 세팅 + Gradle 캐싱 + wrapper 검증.
  - ⚠️ 워크플로는 검사만 "실행". 직접 푸시 금지·검사 통과 강제·리뷰 요구는 저장소 **Branch Protection** 에서 별도 설정(필수 검사명: `build-test-format-lint`). 상세 브랜치 전략은 §10.

> 배포·build-logic·버전카탈로그·providers·example은 이번 범위에서 **의도적으로 제외**.

---

## 4. 목표 디렉토리 구조 (framework-only)

```
InfraStruct/
├── settings.gradle          # framework 모듈 + toolchain resolver(foojay)
├── build.gradle             # 얇음 (실제 설정은 framework/build.gradle)
├── gradle.properties
├── gradle/wrapper/          # Gradle 8.10.2
├── .editorconfig  .gitignore  .gitattributes
├── config/checkstyle/       # checkstyle.xml + suppressions.xml (모듈 공용 룰셋)
├── docs/                    # plan.md(왜) + CONTRIBUTING.md(어떻게) + CONVENTIONS.md(규칙)
└── framework/
    ├── build.gradle         # Java21 + Spotless + SpotBugs + Checkstyle + 테스트
    └── src/
        ├── main/java/com/infrastruct/
        │   ├── api/         # 사용자가 import (InfraStruct.run, @Resource ...)
        │   ├── spi/         # 프로바이더가 구현/상속 — 가장 안정적
        │   └── internal/    # 엔진 전용, 외부 노출 금지
        └── test/java/com/infrastruct/
```

---

## 5. 확장 아키텍처 방향 (spi / api / internal)

패키지를 "기술 종류"가 아니라 **"누가 의존하는가 = 바꾸면 누가 깨지는가"** 로 나눈다.

| 통 | 누가 의존 | 바꾸면 | 안정성 |
|---|---|---|---|
| `com.infrastruct.api` | 사용자(개발자) | 사용자 코드가 깨짐 | 신중 |
| `com.infrastruct.spi` | 프로바이더 | **모든 프로바이더가 깨짐** | **최우선 안정** |
| `com.infrastruct.internal` | 엔진만 | 아무도 안 깨짐 | 자유롭게 리팩터링 |

**판별 규칙**: "엔진 밖의 누군가가 이 타입을 코드에서 직접 쥐는가?"
사용자가 쥐면 api · 프로바이더가 쥐면 spi · 아무도 안 쥐면(결과를 화면으로 볼 뿐) internal.

**발견(discovery) 메커니즘**: `provider="aws"`(String) 를 런타임에 `java.util.ServiceLoader`
(`META-INF/services`)가 발견한 프로바이더들의 이름과 매칭. enum이 아니라 String이라 가능.
(MVP-2의 classgraph 스캔도 가능하나, 프로바이더 등록만큼은 ServiceLoader가 표준·경량.)

**internal 격리 강화** 💡: 지금은 패키지 관례로만 구분. 필요 시 나중에 JPMS `module-info`
또는 Gradle `api`/`implementation` 경계로 물리적 격리.

---

## 6. 배포 — 나중 (구조는 지금, 설정은 나중)

배포는 두 조각. 구조적 결정만 지금 끝내두면 나중에 코드/폴더를 안 뜯어도 된다.

| 구성요소 | 시점 | 이유 |
|---|---|---|
| 구조적 결정 — 모듈=아티팩트 경계, spi/api/internal, 좌표 | ✅ 지금(완료) | 나중에 바꾸면 대공사 |
| 순수 설정 — 배포 플러그인, POM 메타데이터, GPG, Sonatype 계정, 실제 업로드 | ⏸ 나중 | 코드/구조 안 건드림 |

**개발 워크플로(프로바이더 개발 시작할 때)**: framework를 `./gradlew publishToMavenLocal`로
내 PC 창고(`~/.m2`)에 넣고, 프로바이더 레포가 좌표로 가져다 씀. Central 업로드는 그 뒤.

- 좌표: `com.infratect:infrastruct-framework:0.1.0` (지금 값만 정함, 배포는 안 함)
- `example` 모듈 발행 제외 → 애초에 이 레포에 없음(별도 레포)

---

## 7. 개발 환경 요건 ⚠️

- **Gradle 빌드는 JDK 21에서 돌려야 한다.**
  - 시스템의 JBR(IntelliJ 내장, JDK 25)로는 **안 됨** — Gradle 8.10.2는 JDK 23까지만 지원,
    포맷터도 JDK 25에서 깨짐.
  - `build.gradle`의 toolchain이 **컴파일**은 21로 강제하지만, **Gradle 자체를 띄우는 JVM**도 21이어야 함.
- 설정 방법:
  1. IntelliJ: `Settings → Build Tools → Gradle → Gradle JVM` 을 JDK 21로 (없으면 Download JDK → Temurin 21).
  2. 터미널: `brew install openjdk@21` 또는 sdkman `sdk install java 21-tem` 후 `JAVA_HOME` 지정.
- `org.gradle.java.home` 하드코딩은 금지(머신마다 경로가 달라 깨짐).

---

## 8. 이번 세션 결정 로그

- ✅ InfraStruct = 프레임워크(제어의 역전). Terraform을 모델로.
- ✅ 멀티레포: 이 레포 = framework 코어만. 프로바이더/example은 별도 레포.
- ✅ `provider` enum → String (코어가 클라우드를 모름).
- ✅ 팀=InfraTect → groupId `com.infratect`, 아티팩트 `infrastruct-framework`, 버전 `0.1.0`.
- ✅ 지금 세팅 = 포맷터+린터+테스트+spi/api/internal 뼈대. 나머지는 나중.
- ✅ build-logic·버전카탈로그 = 단일 모듈이라 지금 불필요.
- ✅ §5 들여쓰기 = AOSP 4-space.
- ✅ 배포 = 순수 설정이므로 나중(계정 생길 때). 구조만 지금 확정.
- ✅ 프로바이더 개발 키트(Gradle 플러그인) = 나중.
- ✅ 린터 = Error Prone → **SpotBugs** 로 교체(현업 대중성 기준). 포맷터 Spotless 유지. 빌드 그린 재확인.
- ✅ **Checkstyle 추가** — SpotBugs 가 못 보는 소스 레벨 관례(네이밍/임포트/구조)를 담당. 룰셋은 커스텀(`config/checkstyle/`): 포맷 규칙은 Spotless 와 충돌하므로 제외, google_checks/sun_checks 는 각각 2-space 강제·과도한 소음 때문에 미채택.
- ✅ CI = **GitHub Actions `ci.yml`** 추가. main/dev PR·push 검증. 브랜치 보호(직접 푸시 금지·검사 강제·리뷰)는 저장소 설정에서 별도 관리.
- ✅ 브랜치 전략 = **main(배포) / dev(개발)**. 기능은 dev 에서 분기, 긴급 패치만 main 에서 분기. dev·main 병합은 **PR 필수**. (상세 §10)

**2026-08-19 — 예외 처리 · 상태 왕복 타입 제약** (`CONVENTIONS.md` §8 · §9 신설)

- ✅ **예외 vs 결과 객체 = "모아서 보고할 게 있느냐"** 로 가른다. 검증은 위반을 여러 개 모아야 하므로 결과 객체, 상태 파일 파싱 실패는 목록을 모을 수조차 없으므로 예외.
- ✅ **전용 예외는 `internal` 에, unchecked, 모듈당 하나로 시작.** `spi` 에 두면 프로바이더가 인질이 되어 못 고친다. 하나 → 여럿은 안 깨지지만 여럿 → 하나는 깨지므로(하위 타입은 상위 `catch` 에 잡힌다) **되돌릴 수 있는 방향에서 시작**한다. 쪼개는 시점은 `catch` 를 다르게 쓸 호출부가 실제로 생겼을 때.
  - 근거: `ResourceScanException`(resource-scanner)과 `StateStoreException`(current-state-store)이 서로 모른 채 같은 결론에 도달 → 관례로 승격.
- ✅ **`catch` 는 변환용.** 맥락(경로·FQCN)을 붙여 다시 던진다. 삼키는 것은 "에러가 아니라 도메인 사실"일 때만(`NoSuchFileException` → 최초 실행). 판단 기준: *`catch` 안에서 실제로 내릴 결정이 있는가.*
- ✅ **경계에서 한 번 잡는다 = `InfraStruct.run()`.** 엔진 내부는 던지기만. 라이브러리는 `System.exit()` 를 부르지 않는다.
- ✅ **`Kind` 구현체는 enum 필수** — 관례에서 요구사항으로 승격. JSON 왕복에서 값을 복원하려면 값 집합이 닫혀 있어야 한다.
- ✅ **`config` 숫자는 왕복에서 Java 타입이 보존되지 않는다** (JSON 에 `int`/`long` 구분이 없음). 완전한 타입 보존 인코딩은 상태 파일의 가독성을 잃으므로 채택하지 않고, **비교하는 쪽이 값으로 비교**하도록 한다.

---

## 9. 앞으로 결정할 것 (열린 항목)

### 코드 작성하면서 곧 마주칠 것
- 🔶 **spi 계약 확정** — 프로바이더가 구현/상속할 정확한 타입(리소스 루트, Applier/Validator 인터페이스, 등록 지점). 첫 배포 전까지만 자유롭게 바꿀 수 있음.
- 🔶 **ServiceLoader 등록 방식** — 프로바이더 이름("aws")을 무엇에 어떻게 매핑할지, `META-INF/services`에 무엇을 등록시킬지.
- 🔶 **classgraph 유지 여부** — 리소스 스캔은 classgraph, 프로바이더 등록은 ServiceLoader로 갈지, 아니면 스캔도 다른 방식으로 갈지.
- 🔶 **internal 격리 수준** — 패키지 관례만으로 둘지, JPMS `module-info`까지 갈지.
- 🔶 **`config` 숫자 정규화를 `ResourceState` 생성자로 올릴지** — 현재 규칙은 "정수는 `Long`, 소수는 `Double`"
  이고(2026-08-20 확정, `CONVENTIONS.md` §9.3), 파일에서 **읽을 때만** 강제된다. 그래서 config 를
  *채우는* 쪽(`DesiredStateCreator`, 프로바이더의 `Applier`, 테스트 픽스처)이 `int port = 22` 를 그대로
  오토박싱해 `Integer 22` 를 넣으면 복원된 `Long 22` 와 어긋나 **아무것도 안 바꿔도 매번 UPDATE 가 뜬다**
  (apply 해도 같은 값이 다시 저장되므로 사라지지 않는 유령 diff).
  → `ResourceState` 생성자의 `Map.copyOf(config)` 를 정규화 복사로 바꾸면 `Scanned`/`Desired`/`Current` 가
  모두 그 관문을 지나므로 아무도 규칙을 기억할 필요가 없어진다. 규칙을 "지켜야 하는 약속"에서 "어길 수 없는
  구조"로 바꾸는 변경. spi 클래스라 파급이 있어 별도 feature 로 뺀다.
  적용하면 `CurrentStateStoreTest` 픽스처의 `22L` 과 `DesiredStateCreator` Javadoc 의 경고도 함께 정리된다.
  ※ 사용자 코드에는 영향 없다 — 자원 클래스에는 계속 `int port = 22` 라고 쓴다.

### 배포 시점에 결정할 것
- 🔶 **groupId 최종** — `com.infratect`는 Central에서 도메인 소유 증명 필요. 도메인 없으면 `io.github.<계정>`. (지금은 `com.infratect`로 시작, 배포 때 확정)
- 🔶 **버전 정책** — `0.1.0`에서 시작. SemVer 규칙, 언제 첫 릴리스를 끊을지.
- 🔶 **배포 플러그인** — vanniktech `maven-publish` 등. POM 메타데이터(name/description/license/scm/developers).
- 🔶 **Sonatype Central 계정 + GPG 키** — 실제 업로드 준비.

### tier-2 (여유 될 때)
- 💡 **프로바이더 개발 키트** — 공통 빌드 설정을 담은 Gradle 플러그인을 별도 배포(다른 레포가 `plugins { id 'com.infratect...' }` 한 줄로 상속). Terraform SDK 같은 역할.
- 💡 **버전 카탈로그** — 의존성 늘면 `gradle/libs.versions.toml` 도입.
- 💡 **japicmp** — 릴리스 전 바이너리 호환 깨짐 자동 감지(SemVer 기계적 강제).
- 💡 **null 안전성 보강** — SpotBugs 는 이미 도입(§3). NullAway 는 Error Prone 기반이라 제외 → 대안: JSpecify + 정적 체커, 또는 SpotBugs null 검사 심화.

---

## 10. CI / 브랜치 전략 (확정) ✅

**브랜치 모델**
- `main` — 배포 브랜치. 여기서 릴리스가 나간다.
- `dev` — 개발 통합 브랜치.
- 기능 개발: `dev` 에서 브랜치를 파서 작업 → `dev` 로 PR.
- 긴급 패치(hotfix): `main` 에서 브랜치를 파서 작업 → `main` 으로 PR.
- `dev`·`main` 병합은 **PR 필수**(직접 푸시 금지).
- ⚠️ 핫픽스로 `main` 이 앞서가면, `main` 을 다시 `dev` 로 병합해 동기화(안 하면 dev 가 그 수정을 잃음).

**CI (`.github/workflows/ci.yml`)**
- 트리거: `main`/`dev` 로의 PR + 두 브랜치 push.
- 검사: `spotlessCheck`(포맷) → `./gradlew build`(컴파일 + JUnit + SpotBugs + Checkstyle + jar). JDK 21 + Gradle 캐싱 + wrapper 검증.
- 필수 검사명(브랜치 보호에 지정): **`build-test-format-lint`**.

**강제(enforcement) — 저장소 설정에서 별도, 파일 아님** 🔶
- 워크플로는 검사를 "실행"만 한다. 병합 차단은 **Branch Protection Rules**(또는 Rulesets)에서:
  - `main`·`dev` 직접 푸시 금지 / PR 필수
  - 위 필수 검사 통과해야 병합 가능
  - (선택) 리뷰 승인 N명
- GitHub 은 한 번 이상 실행된 검사만 목록에 노출 → 워크플로 push 후 PR 을 한 번 돌려야 필수 지정 가능.
- **미래**: 배포용 `release.yml` 은 §6 배포 단계에서 추가.
