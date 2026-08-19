# summary: resource-scanner (발견·검증 + 필드·참조 추출)

## 무엇을 만들었나

1번 PR(#41)이 시그니처만 못 박아 둔 `ResourceScanner.scan()` 의 본문 전체다. 원래 2번(발견과
검증), 3번(필드와 참조 추출)으로 나눌 계획이었으나 **한 PR 로 합쳤다** (`plan.md` §1-1).

| 단계 | 하는 일 | 근거 |
|---|---|---|
| 수집 | classgraph 로 `@Resource` 클래스를 모은다. `basePackage` 가 있으면 그 범위만 | §3 |
| 정렬 | 클래스 FQCN 순으로 고정한다 | §7-A |
| logicalId | `@Resource(name)` 을 읽고 빈 값, 공백뿐인 값, 중간 공백을 거부한다 | §9 |
| 인스턴스화 | 인자 없는 생성자로 만든다. 사용자의 initializer block 이 이때 실행된다 | §8 |
| kind | `ProviderResource` 인지 확인하고 `kind` 를 읽는다. null 이면 거부한다 | §7-D |
| 필드 순회 | 자식부터 부모로, 클래스 안에서는 필드명순. `kind`/`provider`/static/synthetic 은 skip, shadowing 은 자식이 이긴다 | §7-A, §7-B, §7-F |
| config | 참조가 아닌 non-null 값. null 필드는 키 자체를 안 넣는다 (`Map.copyOf` 제약) | §6 |
| dependencies | `Class` 값과 `Class` 컬렉션을 logicalId 로 푼다. `referencesOf` 메서드로 격리 | §7-C |
| requiredFields | `@Required` 붙은 필드 이름 | §8 |
| annotation 포착 | `@Behavior` 달린 것만, type 이름순 정렬 | §7-A |
| 중복 검사 | 같은 logicalId 를 쓴 두 클래스를 **둘 다** 메시지에 담아 거부한다 | §7-E |

에러는 8종이고 전부 `ResourceScanException`(§9). test 33개(신규 26개), `./gradlew check` 통과.

## PR 을 왜 합쳤나

2번이 push 되기 전이라 지킬 리뷰 이력이 없었고, 이 repo 는 승인 3개가 필요해 PR 을 쪼갤수록
대기가 곱으로 늘어난다. 줄수의 대부분은 fixture 와 test 와 docs 이고 main 코드는
`ResourceScanner` 한 파일이라, 리뷰 부담은 commit 경계(fixture → 구현 → 문서)로 지킨다.

## 통합하면서 결론이 난 미결들

1. **`dependencies` 는 `List<String>` 을 따른다** (`plan.md` §7-C). `DependencyDiff` Javadoc 과
   `Comparator.diffDependencies` 가 이미 "field 자리 = 대상 logicalId" 로 돌고 있는 계약이라
   맞추는 쪽으로 결정했다. 추출 로직은 `referencesOf` 하나로 격리해 타입이 바뀌면 그 메서드와
   test 만 고치면 된다.
2. **`ResourceScanException` 은 `internal` 에 남는다.** #44 가 신설한 `CONVENTIONS.md` §8.3 이
   확정했다. 생성자 2개, `serialVersionUID`, FQCN 메시지 요구도 이미 충족한다.
3. **필드 순회 순서를 필드명 사전순으로 고정했다** (§7-A). `getDeclaredFields()` 는 순서 보장이
   없어, 안 고정하면 `dependencies` 의 원소 순서가 머신마다 달라진다. `ScanEc2` 에 참조 필드를
   2개(`subnet`, `vpc`) 둬서 정렬이 실제로 동작하는지 반증한다.
4. **`@Resource` 없는 `Class` 참조는 스캔 시점에 거부한다** (§9 의 8번째 에러). 조용히 config 로
   흘리면 참조 오타가 숨고 `Class` 객체가 state 파일 직렬화에서 터진다. `bad/dangling/` 이 반증
   fixture 다.
5. **참조가 아닌 `Map` 값은 config 로 간다.** 프로바이더 쪽에 tag 를 `List<Tag>` 에서
   `Map<key, value>` 로 바꾸자는 제안이 있는데, 어느 쪽으로 결정되든 스캐너가 막히지 않도록
   `gammaEc2` 의 `tags` 로 동작을 고정해 뒀다.

## 판단 하나

**`ProviderResource` 미상속 검사를 인스턴스화보다 먼저 한다.** 순서를 뒤집으면 상속도 안 했고
생성자도 없는 클래스가 "생성자가 없다"로 보고된다. 사용자가 먼저 고쳐야 하는 것은 상속 쪽이다.

## 남은 것

1. **`Required` Javadoc 과 다이어그램의 자원 예시** (`plan.md` §13-3). `@Required public Vpc vpc;`
   는 그대로는 컴파일되지 않는다. 남의 파일이라 이번에도 안 건드렸다.
2. **`InfraStruct.run()` 배선.** WBS 상 8/22 부터 정연 님 몫. 스캐너는 입력이 준비된 상태다.
3. **JPMS.** 사용자 코드가 named module 이면 `setAccessible` 이 실패한다. classpath 사용 전제는
   `plan.md` §14 에 남아 있다.

## 다음

`merge` 후 `DesiredStateCreator` 가 `capturedAnnotations` 를 소비하는 feature 로 이어진다.
스캐너가 넘기는 "지시서"(`CapturedAnnotation`)의 handler 호출이 그쪽 몫이다.
