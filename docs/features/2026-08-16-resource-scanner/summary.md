# summary: resource-scanner (발견과 검증)

## 무엇을 만들었나

1번 PR 이 시그니처만 못 박아 둔 `ResourceScanner.scan()` 의 본문을, `plan.md` §1-1 의 2번 범위까지
채웠다. 자원을 찾아내고 logicalId 와 `kind` 를 확정하고 잘못된 선언을 거부하는 데까지다.

| 단계 | 하는 일 | 근거 |
|---|---|---|
| 수집 | classgraph 로 `@Resource` 클래스를 모은다. `basePackage` 가 있으면 그 범위만 | §3 |
| 정렬 | 클래스 FQCN 순으로 고정한다 | §7-A |
| logicalId | `@Resource(name)` 을 읽고 빈 값, 공백뿐인 값, 중간 공백을 거부한다 | §9 |
| 인스턴스화 | 인자 없는 생성자로 만든다. 사용자의 initializer block 이 이때 실행된다 | §8 |
| kind | `ProviderResource` 인지 확인하고 `kind` 를 읽는다. null 이면 거부한다 | §7-D |
| 중복 검사 | 같은 logicalId 를 쓴 두 클래스를 **둘 다** 메시지에 담아 거부한다 | §7-E |

`config`, `dependencies`, `requiredFields`, `capturedAnnotations` 는 아직 비운 채로 넘긴다. 필드
순회는 3번 PR 이고, `scan()` 안에 그 자리 TODO 를 남겨 뒀다.

test 18개(신규 11개), 전체 `./gradlew check` 통과.

## Test fixture 를 이번에 넣었다

`framework/src/test/java/com/infrastruct/fixture/scan/` 아래에 test 전용 자원 계층을 만들었다.
framework 는 프로바이더를 의존하지 않으므로 `AwsVpc` 같은 것을 쓸 수 없는데, **쓰지 않아도 된다는
것 자체가 이 설계의 주장**이다.

- 공통 토대 — `ScanKind`, `ScanProvider`, `ScanResource`
- 자원 타입 4종 — `ScanVpc`, `ScanSubnet`, `ScanEc2`, `ScanRds`
- 정상 자원 5개 (`good/`) — `alphaVpc`, `betaSubnet`, `gammaEc2`, `deltaRds`, `epsilonSubnet`
- 깨진 자원 7 package (`bad/`) — 사유마다 격리했다. 한 package 에 모으면 하나를 스캔할 때 다른
  것도 같이 걸려서 어느 검증이 예외를 냈는지 구분할 수 없다

`bad/**` 를 사유별로 쪼갠 것이 실제로 값을 했다. basePackage 격리가 되지 않으면 에러 test 6개가
서로를 오염시켜, 어느 검증이 동작하는지 알 수 없는 상태로 green 이 된다.

## plan 에서 두 가지가 앞당겨졌다

`plan.md` §1-1 은 `good/**` 와 SpotBugs 예외 목록을 3번 PR 로 잡았는데, 둘 다 이번으로 옮겼다.
문서도 함께 고쳤다.

**`good/**`** — 자원 발견, 순서 고정, `kind` 추출 test 가 전부 `good` 의 자원 5종을 기대값으로 쓴다.
fixture 없이는 red 를 만들 수 없다. 3번 PR 에 남는 fixture 는 매크로 annotation 3종과 핸들러뿐이다.

**`config/spotbugs/exclude.xml`** — 억제 사유가 `setAccessible` 이 아니었다. fixture 의 public
필드를 SpotBugs 가 "안 읽는 필드"(`UrF`, `UuF`)로 오탐한다. 값은 initializer block 이 채우고 읽는
쪽은 스캐너의 reflection 이라 정적 분석에는 둘 다 안 보인다. `ProviderResource` 가
`@SuppressFBWarnings` 로 억제한 것과 같은 건인데, fixture 는 8개 파일에 같은 패턴이 반복되어
파일로 올렸다. 그 파일의 주석이 "한 곳뿐이면 annotation, 반복되면 여기"라는 기준을 남긴다.

## 판단 하나

**`ProviderResource` 미상속 검사를 인스턴스화보다 먼저 한다.** 순서를 뒤집으면 상속도 안 했고
생성자도 없는 클래스가 "생성자가 없다"로 보고된다. 사용자가 먼저 고쳐야 하는 것은 상속 쪽이다.

## 남은 것

1. **`dependencies` 에 필드명을 남길 것인가** (`plan.md` §7-C, §13-1). 이번 범위에서는 `dependencies`
   를 비워 넘기므로 아직 부딪히지 않았다. 3번 PR 에서 `referencesOf` 를 짤 때 결론이 필요하다.
2. **`ResourceScanException` 을 `api` 로 옮길 것인가** (§13-2). 이번 PR 로 실제로 던지는 지점이
   6곳 생겼다. 옮긴다면 3번 PR 전이 싸다.
3. **필드 순회 순서** — `getDeclaredFields()` 는 순서를 보장하지 않는다. §7-A 가 클래스 순서와
   annotation 순서는 고정했는데 필드는 빠져 있다. `dependencies` 가 `List` 라 3번 PR 에서 결과
   순서가 흔들릴 수 있다. `spec.md` 「다음 PR 로 넘기는 행동」 13번으로 올려 뒀다.
4. **`@Resource` 없는 `Class` 값** — 참조처럼 생겼는데 `@Resource` 가 없으면 `plan.md` §8 의 분기상
   조용히 `config` 로 간다. 에러로 잡을지 3번 PR 전에 정해야 한다.

## 다음

**3번 PR** — 필드 순회와 config / dependencies / requiredFields, 매크로 annotation 포착과 그
fixture. `spec.md` 「다음 PR 로 넘기는 행동」의 1~13번. `merge` 된 `dev` 에서 branch 를 딴다.
