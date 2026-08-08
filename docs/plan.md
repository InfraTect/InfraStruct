# InfraStruct 프로젝트 기본 설정 Plan

> 목적: InfraStruct를 **배포 가능한 Java 라이브러리**로 세팅한다.
> InfraStruct는 "뼈대(framework)"이고, 각 클라우드 프로바이더가 이 위에 자신의 자원을
> 코어 수정 없이 추가/배포할 수 있어야 한다. 이 문서는 그 기반 설정의 방향과 결정을 기록한다.
>
> 상태 범례: ✅ 확정 · 🔶 결정 대기 · 💡 tier-2(첫 릴리스 이후)

---

## 0. 배경 — 참고한 것

옆 디렉토리의 두 MVP(`InfraStruct-MVP-2`, `InstraStruct-MVP`)를 조사해서 계승/개선 지점을 정리.

**MVP-2에 이미 있는 것 (계승)**
- Java 21 toolchain, Gradle 8.10.2 wrapper
- 멀티모듈: `framework` / `providers:aws` / `providers:azure` / `example`
- `java-library` 플러그인, JUnit 5(BOM), UTF-8/LF, `.editorconfig`, `.gitattributes`
- 프로바이더는 `framework`에만 의존 (코어는 클라우드를 모른다)

**비어 있는 것 (이번에 채울 것)**
- 포맷터 없음
- 린터/정적분석 없음
- **배포(publishing) 설정 전혀 없음** ← 라이브러리인데 가장 큰 공백
- 공통 설정이 루트 `subprojects {}`에 집중 (Gradle 비권장 방식)
- 의존성 버전이 모듈마다 하드코딩 (중앙 관리 부재)

---

## 1. 확정 스택 요약

| 항목 | 결정 | 상태 |
|---|---|---|
| Java 버전 | **21 LTS** (toolchain), `release=21` 고정 | ✅ |
| 패키지 관리자 | **Gradle** (wrapper) + **Version Catalog** | ✅ |
| 빌드 스크립트 DSL | **Groovy DSL** (`.gradle`) | ✅ |
| 빌드 구조 | **`build-logic` convention plugin** (subprojects{} 대체) | ✅ |
| 포맷터 | **Spotless + google-java-format** | ✅ |
| └ 들여쓰기 변형 | AOSP(4-space) vs 표준(2-space) | 🔶 §5 |
| 린터/정적분석 | **Error Prone** (+ NullAway) | ✅ / NullAway 💡 |
| 테스트 | JUnit 5 + **AssertJ** + Mockito + **JaCoCo** | ✅ |
| 배포 | **vanniktech maven-publish** — 골격만, 자격증명 나중 | ✅ |
| API 호환성 검사 | japicmp | 💡 |

---

## 2. 항목별 상세 & 근거

### 2.1 Java 21 LTS  ✅
- 라이브러리는 **소비자 호환성**이 최우선. `options.release = 21`을 못박아, 개발자가 더 최신 JDK로 빌드하더라도 21 이후 API를 실수로 쓰지 못하게 강제 → 소비자는 21에서 반드시 동작.
- Gradle **toolchain**이 JDK를 자동 인식/다운로드 → "로컬에 JDK 몇 깔렸는지" 문제 제거. VSCode·IntelliJ 모두 인식.
- MVP-2와 동일 버전이라 마이그레이션 비용 0.

### 2.2 Gradle + Version Catalog + Convention Plugin  ✅ (가장 큰 구조 개선)
현재 MVP-2는 `classgraph`/`gson`/`junit` 버전이 파일마다 흩어지고, 공통 설정이 루트 `subprojects {}`에 묶여 있음. 프로바이더가 늘수록 취약.

- **`gradle/libs.versions.toml`** (Version Catalog): 모든 의존성·버전을 한 파일에. `libs.classgraph`처럼 타입세이프 참조.
- **`build-logic/`** (composite build) 안 convention plugin 3종:
  - `infrastruct.java-conventions` — Java 21, 인코딩, 테스트, Spotless, Error Prone 공통
  - `infrastruct.library-conventions` — 위 + `java-library` + 배포 설정
  - `infrastruct.provider-conventions` — 위 + `framework` 의존 자동 주입
- **효과**: 새 프로바이더 모듈 `build.gradle`이 사실상 한 줄:
  ```groovy
  plugins { id 'infrastruct.provider-conventions' }
  // framework 의존 / 포맷터 / 린터 / 배포 전부 상속
  ```

### 2.3 포맷터 — Spotless + google-java-format  ✅
- `./gradlew spotlessApply`(자동 수정) / `spotlessCheck`(CI·검증에서 실패). import 정렬·공백·(선택)라이선스 헤더까지 일괄.
- google-java-format 선택 → **들여쓰기 표준이 2-space**라 현재 `.editorconfig`(4-space)와 충돌. 해소안은 §5 참조.

### 2.4 린터 — Error Prone  ✅
- Checkstyle/PMD는 상당수가 포맷터와 역할이 겹침. **Error Prone**은 컴파일 중 **실제 버그**(자원 누수, `equals`/`==` 오용, null 흐름, 포맷 문자열 등)를 잡아 겹치지 않는 가치를 줌.
- 💡 **NullAway**(null 안전성), 배포 강화용 **SpotBugs**는 첫 릴리스 이후 tier-2로 추가 가능.

### 2.5 테스트 — JUnit 5 유지 + 보강  ✅
- **JUnit 5 (Jupiter)**: 현행 유지.
- **AssertJ**: `assertThat(plan.actions()).extracting(...).containsExactly(...)` 류 가독성. 인프라 상태 diff 검증에 강함.
- **Mockito**: `CloudProvider` 목킹(이미 `MockAwsProvider` 존재 → 궁합 좋음).
- **JaCoCo**: `./gradlew test jacocoTestReport` 커버리지. 배포 라이브러리라 회귀 방어에 중요.

### 2.6 배포 — vanniktech maven-publish (골격만)  ✅
- 순정 `maven-publish`는 보일러플레이트가 큼. **vanniktech `gradle-maven-publish-plugin`**이
  `maven-publish` + `signing` + javadoc jar + sources jar + POM 메타데이터 + Sonatype **Central Portal** 업로드를 하나로 처리.
- **이번 범위**: 플러그인 적용 + POM 메타데이터(name/description/license/scm/developers) + 모듈별 아티팩트 분리 + `mavenLocal()` 발행 검증까지.
- **나중**: Sonatype 계정, GPG 키, 네임스페이스 검증, 실제 Central 업로드는 첫 릴리스 시점에.
- 주의:
  1. `com.infrastruct` groupId는 Central에서 **도메인 소유 증명** 필요. 도메인 없으면 `io.github.<계정>` 사용(GitHub 계정으로 즉시 검증).
  2. **프로바이더는 각각 별도 아티팩트**(`infrastruct-framework`, `infrastruct-provider-aws` …)로 발행 → 소비자가 필요한 것만 의존. (AWS SDK v2 / Testcontainers 모델)
  3. `example` 모듈은 발행 제외.

---

## 3. 프로바이더 확장 아키텍처 방향

"뼈대" 프로젝트의 본질. 기반 설정 단계에서 미리 못박아야 할 경계들.

1. **모듈 경계** ✅ — 프로바이더는 `providers:<name>` 독립 모듈, **`framework`에만** 의존(현행 유지). 코어는 어떤 클라우드도 몰라야 한다.
2. **공개 API 표면 분리** ✅ — `framework` 패키지를 확장 지점 기준으로 분할:
   - `com.infrastruct.spi` : 프로바이더가 **구현할** 인터페이스(`CloudProvider`, 리소스 계약). **여기가 안정적이어야 함.**
   - `com.infrastruct.api` : 사용자가 쓰는 공개 API
   - `com.infrastruct.internal` : 외부 노출 금지 (Gradle `implementation`으로 새어나가지 않게 격리)
3. **발견(discovery) 메커니즘** ✅ — `docs/change-provider-architecture.md`의 `@RegisterProvider` 방향을 **`java.util.ServiceLoader`**(`META-INF/services`) 표준으로 구현 권장. classgraph 스캔도 가능하나, 프로바이더 등록만큼은 ServiceLoader가 표준·경량·배포 후 안정적.
4. **API 호환성 검사** 💡 — `japicmp`로 이전 릴리스 대비 **바이너리 호환 깨짐을 릴리스 전 자동 감지**. 남의 프로바이더가 우리 위에 서므로 SemVer를 기계적으로 강제. (첫 릴리스 이후 도입)

---

## 4. 목표 디렉토리 구조

```
InfraStruct/
├── settings.gradle              # 모듈 + build-logic 포함
├── build.gradle                 # 얇게 (공통 설정은 convention plugin으로 이동)
├── gradle.properties
├── gradle/
│   ├── wrapper/                 # Gradle 8.10.2 (MVP-2 계승)
│   └── libs.versions.toml       # ★ 버전 카탈로그 (신규)
├── build-logic/                 # ★ convention plugins (신규)
│   ├── settings.gradle
│   └── src/main/groovy/
│       ├── infrastruct.java-conventions.gradle
│       ├── infrastruct.library-conventions.gradle
│       └── infrastruct.provider-conventions.gradle
├── .editorconfig  .gitattributes  .gitignore   # 계승/조정
├── framework/                   # spi / api / internal 로 패키지 재구성
├── providers/
│   ├── aws/
│   └── azure/
└── example/                     # 발행 제외
```

---

## 5. 🔶 결정 대기: `.editorconfig` 들여쓰기 (google-java-format 결과)

google-java-format의 표준은 **2-space**. 현재 `.editorconfig`는 Java도 4-space라 포맷터와 충돌. 택일 필요:

- **(A) 표준 2-space** — `.editorconfig`에 `[*.java] indent_size = 2` 추가. 도구 간 100% 일치. Gradle/기타는 4-space 유지.
- **(B) AOSP 4-space (기본 제안)** — Spotless에서 `googleJavaFormat().aosp()` 지정 → google 스타일을 유지하되 4-space. 현행 `.editorconfig` 4-space 관례 안 깨짐.

> 무응답 시 **(B) AOSP 4-space**로 진행 예정. (지금 4-space 관례 보존)

---

## 6. 실행 순서 (합의 후 진행)

1. Gradle wrapper + `gradle.properties` + `.editorconfig`/`.gitattributes`/`.gitignore` 정비 (MVP-2 계승 + §5 반영)
2. `gradle/libs.versions.toml` 작성 → 의존성 버전 중앙화
3. `build-logic` convention plugin 3종 작성, 루트 `subprojects {}` 제거
4. Spotless(google-java-format) + Error Prone + JaCoCo를 convention plugin에 통합
5. `framework` 패키지 spi/api/internal 재배치 + ServiceLoader 등록 골격
6. vanniktech 배포 플러그인 + POM 메타데이터 + 모듈 아티팩트 분리 (자격증명 제외, `mavenLocal` 검증)
7. `spotlessCheck` · `test` · `build` 그린 확인 → README/문서화

---

## 7. 미해결/추후 논의

- 🔶 §5 들여쓰기 A/B 최종 선택
- 💡 NullAway, SpotBugs 도입 시점
- 💡 japicmp API 호환성 게이트
- 🔶 groupId 최종 확정: `com.infrastruct`(도메인 필요) vs `io.github.<계정>`
- 🔶 초기 버전 번호: MVP-2는 `0.2.0` — 신규 저장소는 `0.1.0`부터? 아니면 계승?
- 🔶 Gradle 버전: MVP-2의 8.10.2 유지 vs 최신 8.x 업그레이드
