# summary: resource-scanner (뼈대)

## 무엇을 만들었나

`com.infrastruct.internal.ResourceScanner` — 사용자가 `@Resource` 로 선언한 클래스를 reflection 으로
읽어 `ScannedResources` 로 바꾸는 내부 모듈의 **뼈대(스텁)**. 공개 시그니처만 확정하고 본문은 비웠다.

| 멤버 | 계약 | 현재 동작(스텁) |
|---|---|---|
| `ResourceScanner()` | classpath 전체를 스캔한다 | `basePackage` 를 `null` 로 보관 |
| `ResourceScanner(String)` | 주어진 package 아래만 스캔한다 | 인자를 그대로 보관 |
| `ScannedResources scan()` | `@Resource` 클래스를 모두 찾아 스캔 결과로 변환 | 항상 빈 `ScannedResources` 반환 |

전용 예외 `com.infrastruct.internal.ResourceScanException` 도 함께 확정했다. `RuntimeException` 을
상속하고 `(String)` 과 `(String, Throwable)` 두 생성자를 둔다.

파이프라인에서의 자리(`InfraStruct.run()`): 입구. `scan()` 결과가 `DesiredStateCreator.create()` 의
입력이 된다.

테스트 7개 추가(`ResourceScannerTest`), 전체 `./gradlew check` 통과.

## 왜 뼈대만인가 — 리뷰 가능한 크기

`DesiredStateCreator` 뼈대는 "설계 전제가 아직 안 섰다"가 이유였지만, 이번은 다르다. 스캔 로직 자체는
설계가 다 서 있다. 나눈 이유는 **분량**이다.

구현 전체를 한 번에 올리면 1,691줄이다. 900줄도 많다는 지적이 이미 있었고, 그 크기는 리뷰가 형식적으로
흐른다. 그래서 `plan.md` §1-1 처럼 세 PR 로 나눴다.

| PR | 범위 | 대략 |
|---|---|---|
| **1 (이 PR)** | 뼈대 + 설계 문서 | ~800줄 (대부분 문서) |
| 2 | 발견과 검증 + `fixture/scan/bad/**` | ~480줄 |
| 3 | 필드와 참조 추출 + `fixture/scan/good/**` + SpotBugs 예외 | ~490줄 |

1번이 문서 쪽으로 무거운 것은 의도한 것이다. 설계 판단(§5 의 `Class` 값 참조, §6 의 `Map.copyOf` null
제약, §7-C 의 `dependencies` 미결)을 여기서 한 번에 검토받고, 2번과 3번은 그 판단을 코드로 옮기는
것만 보면 되게 한다.

`fixture/scan/**` 를 이번에 넣지 않은 이유도 같다. 본문이 비어 있으면 fixture 로 검증할 대상이 없어서,
지금 올리면 아무 test 도 걸리지 않은 368줄이 된다. 각 fixture 는 그것을 실제로 쓰는 PR 과 함께 간다.

작업본은 `resource-scanner-wip` 브랜치(`d609a97`)에 보관했다. 설계 근거가 실제로 도는 코드에서 나온
것이라는 뜻이고, 2번과 3번은 그것을 대조군으로 두고 다시 유도한다.

## 스텁인데도 확정해 둔 것

**`basePackage` 생성자.** 이번 범위에서 쓰이지 않지만 시그니처는 지금 연다. test 가 정상 fixture 와
깨진 fixture 를 서로 다른 package 에 격리해 골라 스캔하는 구조(`spec.md` 「Test fixture」)가 여기에
걸려 있고, 나중에 `InfraStruct.run(mainClass)` 가 `mainClass.getPackageName()` 을 넘기는 경로도
같다(`plan.md` §3).

**`ResourceScanException`.** 본문이 없어 아직 던질 일이 없지만 계약이라 함께 못 박았다. MVP-2 가
전부 `IllegalStateException` 이던 것을 전용 타입으로 바꾸는 것이 `plan.md` §9 의 판단이고, 이건
"나중에 리팩터링"이 아니라 지금 정해야 호출부가 catch 할 타입을 안다.

**`basePackage()` 접근자(package-private).** 스텁 단계에서 생성자 인자가 실제로 보관되는지 확인할
길이 이것뿐이라 열었다. `scan()` 이 빈 결과만 주는 동안에는 간접 검증이 불가능하다. 2번 PR 에서
`findAnnotatedClasses()` 가 이 값을 쓰기 시작한다.

## 남의 파일은 건드리지 않았다

- `spi/Required.java` 의 Javadoc 예시(`@Required public Vpc vpc;`)는 컴파일되지 않는 예시다
  (`plan.md` §5). 남의 파일이라 고치지 않고 열린 질문 §13-3 으로 올렸다.
- `dependencies` 타입(`List<String>`)이 EC2 의 참조 5종과 RDS 의 subnet 배열을 표현하지 못하는
  문제(`plan.md` §7-C)도 `spi` 계약이고 `Comparator` 에 걸려서 이 feature 안에서 결론내지 않는다.
  3번 PR 에서 추출을 메서드 하나로 격리해, 계약이 바뀌면 평탄화 한 줄과 그 test 만 고치게 둔다.

## 위치 — `internal` (spi 아님)

`Comparator` · `CurrentStateStore` · `DesiredStateCreator` 와 같은 `internal` 패키지. 상태 그릇
(`ScannedResources`, `ScannedResourceState`)만 `spi` 에 있고, 그 그릇을 만드는 엔진 모듈은
`internal` 에 둔다. 클래스 다이어그램에서도 `ResourceScanner` 박스는 무색(프레임워크 내부 코드)이다.

## 열린 질문 (그대로 남음)

1. `dependencies` 에 필드명을 남길 것인가 (`plan.md` §7-C, §13-1). `spi` 변경이라 팀 결정이 필요하다.
2. `ResourceScanException` 을 `api` 로 옮길 것인가 (`plan.md` §9, §13-2). 이 예외는
   `InfraStruct.run()` 을 타고 사용자에게 올라간다. 사용자가 catch 해야 하는 타입이면 `api` 다.
   배선 feature(8/22)에서 결론이 날 항목이라 지금은 `internal`.
3. `Required` Javadoc 과 다이어그램의 자원 예시를 누가 언제 고칠 것인가 (`plan.md` §13-3).

## 다음

- **2번 PR** — classgraph 수집, FQCN 정렬, logicalId 검증, 인스턴스화, `kind` 추출, 중복 검사와
  `fixture/scan/bad/**`. `spec.md` 「다음 PR 로 넘기는 행동」의 1~10번.
- **3번 PR** — 필드 순회와 config / dependencies / requiredFields, 매크로 annotation 포착,
  `fixture/scan/good/**`, SpotBugs 예외 목록. 같은 목록의 11~22번.
- 두 브랜치 모두 직전 브랜치가 아니라 **merge 된 `dev` 에서 딴다** (`plan.md` §1-1).
