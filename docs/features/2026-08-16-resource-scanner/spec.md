# spec: resource-scanner

`plan.md` 를 테스트 가능한 **행동 목록**으로 옮긴 것. 아래 `- ` 불릿들이 첫 `/red` 때
체크리스트(`behaviors[]`)로 등록되고, 위에서부터 순서대로 red→green 을 돈다.

이번 PR 은 **뼈대**다(근거: `plan.md` §1, §1-1). 그래서 행동 목록은 "시그니처가 존재하고 계약대로
생겼다" 수준에서 멈춘다. reflection 결과의 정확성을 보는 행동들은 2번과 3번 PR 의 spec 으로 넘긴다.
아래 「다음 PR 로 넘기는 행동」에 목록을 남겨 두므로, 그 PR 의 spec 은 여기서 잘라 가면 된다.

## 행동 목록 (red 사이클 순서)

- ResourceScanner 는 인자 없이, 그리고 basePackage 문자열로 인스턴스를 만들 수 있다
- 생성자에 넘긴 basePackage 를 그대로 보관하고 인자 없는 생성자는 전체 스캔을 뜻하는 null 이 된다
- scan() 은 스텁이라 원소 없는 ScannedResources 를 돌려준다
- ResourceScanException 은 RuntimeException 이고 메시지를 그대로 보존한다
- ResourceScanException 은 원인 예외를 cause 로 보존한다

## 다음 PR 로 넘기는 행동

`plan.md` §1-1 의 분할을 따른다. 아래는 지금 등록하지 않는다. 각 항목에 **어떻게 확인하는지**를
함께 적어 뒀으므로 그 PR 의 spec 은 여기서 잘라 가면 된다. 하네스가 `- ` 불릿을 행동으로 자동
등록하므로 여기서는 일부러 번호 목록을 쓴다.

기대값은 위 fixture 표에서 온다. `GOOD` 은 `"com.infrastruct.fixture.scan.good"` 상수로 둔다.

### 2번 PR — 발견과 검증

1. **자원 발견과 logicalId** — `@Resource` 가 붙은 클래스를 모두 찾고 logicalId 는 `name()` 값
   그대로다. `good` 만 스캔했을 때 `resources()` 크기가 5 이고 logicalId 집합이
   `{alphaVpc, betaSubnet, gammaEc2, deltaRds, epsilonSubnet}` 이다.
2. **basePackage 격리** — 범위 밖의 자원은 스캔하지 않는다. `good` 만 스캔하면 `bad` 의 깨진
   자원이 걸리지 않아 예외가 나지 않는다. 이것이 성립해야 아래 에러 test 들이 서로를 오염시키지 않는다.
3. **순서 고정** — 결과 순서가 클래스 이름순이다. logicalId 를 순서대로 뽑은 리스트가 클래스 FQCN
   을 정렬한 순서와 일치한다. 상수로 박지 말고 fixture 클래스 이름에서 기대값을 계산해 비교한다.
4. **kind** — `ProviderResource` 필드에서 읽는다. `alphaVpc` 의 `kind()` 가 `ScanKind.Vpc` 와
   `assertSame` 이다. 나머지도 각각 확인한다.
5. **에러 6종** — 각 `bad/*` package 를 basePackage 로 스캔하면 `ResourceScanException` 이 난다
   (빈 name, 공백뿐인 name, 중간 공백, `ProviderResource` 미상속, `kind` null, 생성자 없음).
   `assertThatThrownBy(...).isInstanceOf(ResourceScanException.class).hasMessageContaining(FQCN)`
   형태로, 메시지에 문제 클래스의 FQCN 이 들어 있는지까지 본다.
6. **중복 logicalId** — `bad/dup/` 스캔 시 메시지에 충돌한 **두 클래스 이름이 모두** 들어 있다.
   하나만 알려 주면 사용자가 나머지 하나를 직접 찾아야 한다.
7. **cause 보존** — `bad/noctor/` 의 예외는 `getCause()` 가 `ReflectiveOperationException` 계열이다.
   원인을 삼키면 사용자가 진짜 이유를 못 본다.

### 3번 PR — 필드와 참조 추출

8. **메타 필드 제외** — 모든 자원에 대해 `config()` 에 `"kind"`, `"provider"` 키가 없고
   `dependencies()` 에도 그 값이 섞이지 않는다.
9. **스칼라 필드** — `betaSubnet` 의 `config()` 에 `cidrBlock=10.0.1.0/24` 가 있고, `gammaEc2` 의
   `config()` 에 `instanceType=t3.micro` 가 있다.
10. **조부모 필드** — `alphaVpc` 의 `config()` 에 `owner=infra-team` 이 있다. `ScanResource` 는
    `GoodVpc` 의 조부모다.
11. **shadowing** — `gammaEc2` 의 `config()` 의 `owner` 가 `team-b` 다. 부모 값 `infra-team` 이
    아니어야 하고, `owner` 키가 중복으로 두 번 들어가지도 않는다.
12. **단일 참조** — 값이 `Class` 이고 그 클래스에 `@Resource` 가 있으면 dependencies 에 그 name 이
    들어간다. `betaSubnet` 의 `dependencies()` 가 `alphaVpc` 를 담고 `config()` 에는 `vpc` 키가 없다.
13. **컬렉션 참조** — 원소마다 dependencies 에 들어간다. `deltaRds` 의 `dependencies()` 가
    `betaSubnet` 과 `epsilonSubnet` 을 모두 담고 `config()` 에 `subnets` 키가 없다. 이 test 가
    실패하면 RDS 를 표현할 수 없다는 뜻이다.
14. **null 필드** — `betaSubnet` 의 `config()` 에 `az` 키가 **없다**. `null` 값이 들어 있는 게
    아니라 키 자체가 없어야 한다. `Map.copyOf` 가 null 값에 NPE 를 던지므로 이건 구현 제약이기도
    하다(`plan.md` §6).
15. **requiredFields** — `betaSubnet` 의 `requiredFields()` 가 `{vpc, cidrBlock}` 이다. 참조 필드
    `vpc` 가 빠지면 안 된다. `az` 는 `@Required` 가 없으므로 포함되지 않는다.
16. **매크로 annotation 포착** — `@Behavior` 가 달린 것만 포착하고 `@Resource` 는 제외한다.
    `betaSubnet` 의 `capturedAnnotations()` 크기가 1 이고 그 `handlerClass()` 가 `TagHandler.class` 다.
17. **annotation 인스턴스** — 16 에서 꺼낸 것을 `Tagged` 로 캐스팅해 `value()` 가 `"net"` 이다.
    붙어 있던 값이 그대로 와야 한다.
18. **annotation 정렬** — `gammaEc2` 의 `capturedAnnotations()` 가 `Encrypted`, `Tagged` 순이다
    (type 이름 사전순). 선언 순서는 `@Tagged @Encrypted` 라 정렬이 실제로 동작해야 뒤집힌다.
19. **@Behavior 없는 annotation** — 16 의 확장. `betaSubnet` 에 `@Plain` 이 붙어 있는데도 포착
    목록에 없다는 것을 따로 단언한다.

## 공개 인터페이스 시그니처 (확정)

### `com.infrastruct.internal.ResourceScanner`

```java
public final class ResourceScanner {

    /** classpath 전체를 스캔한다. */
    public ResourceScanner() { ... }

    /** 주어진 package 아래만 스캔한다. null 또는 blank 면 전체 스캔. */
    public ResourceScanner(String basePackage) { ... }

    /** @Resource 가 붙은 클래스를 모두 찾아 스캔 결과로 바꾼다. */
    public ScannedResources scan() { ... }
}
```

### `com.infrastruct.internal.ResourceScanException`

```java
/** 자원 선언이 잘못되어 스캔을 진행할 수 없을 때 던진다. */
public class ResourceScanException extends RuntimeException {

    public ResourceScanException(String message) { ... }

    public ResourceScanException(String message, Throwable cause) { ... }
}
```

`RuntimeException` 인 이유는 `plan.md` §9 참조. 사용자의 선언 실수라 호출부가 복구할 수 없다.

> 🔶 package 배치는 미결이다(`plan.md` §13-2). 지금은 `internal` 로 두지만, 이 예외는
> `InfraStruct.run()` 을 타고 사용자에게까지 올라간다. 사용자가 catch 해야 하는 타입이면
> `api` 로 옮겨야 한다. 배선 feature(정연 님, 8/22)에서 결론이 날 항목이라 이번엔 `internal`.

## Test fixture (2번, 3번 PR)

> 이번 PR 에는 들어가지 않는다. 본문이 비어 있으면 검증할 대상이 없어서, 각 fixture 는 그것을 실제로
> 쓰는 PR 과 함께 올린다(`plan.md` §12). 설계는 지금 확정해 두고 아래를 그대로 따른다. `bad/**` 는
> 2번 PR, `good/**` 와 매크로 annotation 은 3번 PR 이다.

`framework/src/test/java/com/infrastruct/fixture/scan/` 아래에 둔다. framework 는 프로바이더를
의존하지 않으므로 test 전용 자원 계층을 직접 만든다.

> 클래스 본문은 적지 않는다. 실제로 돌려 본 fixture 가 `resource-scanner-wip` (`d609a97`) 에 그대로
> 있으므로, 코드가 필요하면 그쪽을 열면 된다. 여기에는 **무엇을 왜 두는지**만 남긴다.

### 공통 토대

- `ScanKind` — `Kind` 를 구현한 enum. `Vpc`, `Subnet`, `Ec2`, `Rds`
- `ScanProvider` — `Provider` 상속. 내용 없음
- `ScanResource` — `ProviderResource` 상속. `provider` 를 채우고 `owner = "infra-team"` 필드를 둔다.
  실제 프로바이더의 `AwsResource` 자리다. `owner` 는 **조부모 필드까지 읽는지** 보는 장치다

### 자원 타입

`ScanResource` 를 상속하고 각자 `kind` 를 채운다. 필드 구성이 곧 검증 장치다.

| 클래스 | 필드 | 무엇을 반증하나 |
|---|---|---|
| `ScanVpc` | `@Required cidrBlock` | 스칼라 필드가 config 로 |
| `ScanSubnet` | `@Required vpc`(`Class<? extends ScanVpc>`), `@Required cidrBlock`, `az` | 단일 참조. `az` 는 값을 안 넣어 **null 필드**를 만든다 |
| `ScanEc2` | `@Required subnet`, `instanceType = "t3.micro"` | 기본값이 있는 스칼라 |
| `ScanRds` | `@Required subnets`(`List<Class<? extends ScanSubnet>>`), `engine = "mysql"` | **컬렉션 참조**. RDS 가 subnet 2개 이상을 요구하는 것을 본뜸(`plan.md` §5) |

참조 필드가 `Class<? extends T>` 인 이유는 `plan.md` §5 다. 다이어그램의 `@Required public Vpc vpc;`
는 그대로는 컴파일되지 않는다.

`ScanRds` 는 컬렉션 참조 지원의 반증 장치다. 없으면 단일 참조만 처리하는 구현이 그냥 통과한다.

### 매크로 annotation 과 핸들러

셋 다 `RUNTIME` retention 에 `TYPE` target 이다.

| annotation | `@Behavior` | 비고 |
|---|---|---|
| `Tagged` | `handler = TagHandler.class` | `String value() default "default-tag"` 멤버를 둔다 |
| `Encrypted` | `handler = EncryptHandler.class` | 멤버 없음 |
| `Plain` | **없음** | 포착되면 안 된다 |

`Tagged` 에 멤버를 둔 이유는 포착된 것이 **실제로 붙어 있던 인스턴스**인지 확인하기 위해서다.
멤버가 없으면 새로 만든 빈 annotation 을 넣어도 test 가 통과한다.

`Plain` 을 두는 이유는 "`@Behavior` 가 달린 것만 포착한다"를 반증 가능하게 만들기 위해서다.
매크로 annotation 만 있으면 전부 포착하는 구현도 test 를 통과해 버린다.

### 정상 자원 (`fixture/scan/good/`)

| logicalId | 클래스 | 채우는 값 | 붙인 annotation |
|---|---|---|---|
| `alphaVpc` | `GoodVpc extends ScanVpc` | `cidrBlock = "10.0.0.0/16"` | 없음 |
| `betaSubnet` | `GoodSubnet extends ScanSubnet` | `vpc = GoodVpc.class`, `cidrBlock = "10.0.1.0/24"`, `az` 는 비움 | `@Tagged("net")`, `@Plain` |
| `gammaEc2` | `GoodEc2 extends ScanEc2` | `subnet = GoodSubnet.class`, `owner = "team-b"` | `@Tagged @Encrypted` |
| `deltaRds` | `GoodRds extends ScanRds` | `subnets = List.of(GoodSubnet.class, OtherSubnet.class)` | 없음 |
| `epsilonSubnet` | `OtherSubnet extends ScanSubnet` | 컬렉션 원소를 2개로 만들기 위한 두 번째 subnet | 없음 |

의도한 함정이 셋이다. `betaSubnet` 의 `az` 를 비워 **null 필드가 config 에 안 들어가는지** 보고,
`gammaEc2` 의 `owner` 로 `ScanResource.owner` 를 **shadowing** 해 자식 값이 이기는지 본다.
`gammaEc2` 에 annotation 을 `@Tagged @Encrypted` 순으로 붙인 것은 **정렬이 실제로 뒤집는지** 보려는
것이다. type 이름순이면 `Encrypted` 가 앞이어야 한다.

### 깨진 자원 (`fixture/scan/bad/<사유>/`)

사유마다 **다른 package 로 격리**한다. 한 package 에 모으면 하나를 스캔할 때 다른 것도 같이
걸려서 어느 검증이 예외를 냈는지 구분할 수 없다.

| package | 내용 |
|---|---|
| `bad/blank/` | `@Resource(name = "")` |
| `bad/whitespace/` | `@Resource(name = "   ")` |
| `bad/inner/` | `@Resource(name = "my ec2")` |
| `bad/noctor/` | 인자 있는 생성자만 선언 |
| `bad/notprovider/` | `ProviderResource` 를 상속하지 않음 |
| `bad/nokind/` | `ScanResource` 는 상속하되 `kind` 를 안 채움 |
| `bad/dup/` | 서로 다른 두 클래스가 같은 `@Resource(name = "twin")` |

## 이번 범위에서 검증하지 않는 것

1. `DesiredStateCreator` 가 `capturedAnnotations` 를 실제로 소비하는 동작. 스캐너는 "지시서"를
   모아서 넘기기만 한다. 소비는 그쪽 feature 의 몫이다.
2. 의존성의 **순환 참조** 검출. 위상 정렬은 `PlanCreator` 담당이다.
3. `dependencies` 에 필드명을 남기는 형태(`plan.md` §7-C). 계약이 정해지면 별도 feature 에서
   `referencesOf` 격리 메서드와 그 test 만 고친다.
4. provider 별 필터링. 스캐너는 발견한 것을 전부 넘긴다.
5. **"필드 값을 읽지 못함" 에러** (`plan.md` §9 표의 7번째 행). 구현에는 남기지만 행동 목록에서는
   뺐다. `setAccessible(true)` 를 부른 뒤 classpath 상의 클래스에서 `IllegalAccessException` 을
   일으킬 방법이 없어서 red 를 만들 수가 없다. 억지로 mock 을 세우면 reflection 을 mock 하는
   test 가 되어 검증 가치보다 유지비가 크다. 방어 코드로만 두고 커버리지에서 빠지는 것을 감수한다.
   그래서 `plan.md` 는 에러 7종, 이 문서의 행동 목록은 6종이다.
