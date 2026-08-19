# spec: resource-scanner

`plan.md` 를 테스트 가능한 **행동 목록**으로 옮긴 것. 아래 `- ` 불릿들이 첫 `/red` 때
체크리스트(`behaviors[]`)로 등록되고, 위에서부터 순서대로 red→green 을 돈다.

이번 PR 은 **발견·검증과 필드·참조 추출 전부**다(근거: `plan.md` §1-1. 원래 2번과 3번으로
나눌 계획이었으나 하나로 합쳤다). 자원을 찾아내고 logicalId 와 `kind` 를 확정하고 잘못된 선언을
거부한 뒤, 필드를 순회해 config / dependencies / requiredFields 를 채우고 매크로 annotation 을
포착하는 데까지 간다.

## 행동 목록 (red 사이클 순서)

- ResourceScanner 는 인자 없이, 그리고 basePackage 문자열로 인스턴스를 만들 수 있다
- 생성자에 넘긴 basePackage 를 그대로 보관하고 인자 없는 생성자는 전체 스캔을 뜻하는 null 이 된다
- ResourceScanException 은 RuntimeException 이고 메시지를 그대로 보존한다
- ResourceScanException 은 원인 예외를 cause 로 보존한다
- @Resource 가 붙은 클래스를 모두 찾고 logicalId 는 name() 값 그대로다
- basePackage 밖의 자원은 스캔하지 않아 good 만 스캔하면 bad 의 깨진 자원이 걸리지 않는다
- 결과 순서가 클래스 FQCN 순으로 고정된다
- kind 를 ProviderResource 에서 읽어 fixture 의 enum 상수와 동일한 인스턴스를 돌려준다
- 빈 name 과 공백뿐인 name 과 중간에 공백이 섞인 name 은 각각 거부하고 메시지에 FQCN 을 담는다
- ProviderResource 미상속과 kind null 과 인자 없는 생성자 부재도 각각 거부한다
- logicalId 가 중복이면 충돌한 두 클래스 이름이 모두 메시지에 담긴다
- 인스턴스화 실패의 원인 예외를 ReflectiveOperationException 계열 cause 로 보존한다
- 모든 자원의 config 에 kind 와 provider 키가 없다
- 스칼라 필드가 config 에 이름과 값으로 들어간다 (betaSubnet 의 cidrBlock, gammaEc2 의 instanceType)
- 조부모 클래스의 필드까지 읽는다 (alphaVpc 의 owner 가 infra-team)
- 자식이 shadowing 한 필드는 자식 값이 이긴다 (gammaEc2 의 owner 가 team-b)
- 값이 null 인 필드는 config 에 키 자체가 없다 (betaSubnet 의 az. Map.copyOf 의 NPE 제약이기도 하다)
- 참조가 아닌 Map 값 필드는 config 로 간다 (gammaEc2 의 tags)
- @Required 필드 이름이 requiredFields 에 모인다. 참조 필드도 포함된다 (betaSubnet 의 {vpc, cidrBlock})
- 값이 Class 이고 @Resource 가 있으면 dependencies 에 그 name 이 들어가고 config 에는 없다
- 컬렉션 참조 필드의 원소가 각각 dependencies 에 들어간다 (deltaRds 의 betaSubnet, epsilonSubnet)
- dependencies 의 순서가 필드명 사전순으로 고정된다 (gammaEc2 가 subnet→betaSubnet, vpc→alphaVpc 순)
- @Resource 없는 클래스를 가리키는 참조는 거부되고 메시지에 양쪽 FQCN 이 담긴다
- @Behavior 가 달린 annotation 만 포착하고 handlerClass 를 함께 담는다
- 포착된 annotation 은 실제 붙어 있던 인스턴스라 멤버 값을 읽을 수 있다 (Tagged.value() 가 "net")
- 포착 순서가 annotation type 이름순으로 고정된다 (선언은 @Tagged @Encrypted, 결과는 Encrypted 먼저)
- @Behavior 없는 annotation 은 포착되지 않는다 (@Plain)

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

> package 배치는 `CONVENTIONS.md` §8.3 (#44) 이 `internal` 로 확정했다. 예외를 잡는 쪽은
> 경계(`InfraStruct.run()`)의 엔진 자신이라 "엔진 밖에서 직접 쥐는 타입" 이 아니다.

## Test fixture

> **이번 PR 에 전부 들어간다:** 공통 토대, 자원 타입 4종, `good/**` 5개, `bad/**` 8개 package,
> 매크로 annotation(`Tagged`, `Encrypted`, `Plain`)과 핸들러 2종.

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
| `ScanEc2` | `@Required subnet`, `@Required vpc`, `tags`(`Map`), `instanceType = "t3.micro"` | 기본값이 있는 스칼라, **참조 필드 2개**(순서 고정 반증), 참조가 아닌 `Map` 값 |
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
| `gammaEc2` | `GoodEc2 extends ScanEc2` | `subnet = GoodSubnet.class`, `vpc = GoodVpc.class`, `tags = Map.of("team", "infra")`, `owner = "team-b"` | `@Tagged @Encrypted` |
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
| `bad/dangling/` | 참조 필드가 `@Resource` 없는 클래스(`NotAResourceVpc`)를 가리킴 |

## 이번 범위에서 검증하지 않는 것

1. `DesiredStateCreator` 가 `capturedAnnotations` 를 실제로 소비하는 동작. 스캐너는 "지시서"를
   모아서 넘기기만 한다. 소비는 그쪽 feature 의 몫이다.
2. 의존성의 **순환 참조** 검출. 위상 정렬은 `PlanCreator` 담당이다.
3. `dependencies` 에 필드명을 남기는 형태. `plan.md` §7-C 에서 현재 계약(`List<String>`)을
   따르기로 결정했다. 계약이 바뀌면 별도 feature 에서 `referencesOf` 격리 메서드와 그 test 만 고친다.
4. provider 별 필터링. 스캐너는 발견한 것을 전부 넘긴다.
5. **"필드 값을 읽지 못함" 에러** (`plan.md` §9 표의 7번째 행). 구현에는 남기지만 행동 목록에서는
   뺐다. `setAccessible(true)` 를 부른 뒤 classpath 상의 클래스에서 `IllegalAccessException` 을
   일으킬 방법이 없어서 red 를 만들 수가 없다. 억지로 mock 을 세우면 reflection 을 mock 하는
   test 가 되어 검증 가치보다 유지비가 크다. 방어 코드로만 두고 커버리지에서 빠지는 것을 감수한다.
   그래서 `plan.md` 는 에러 7종, 이 문서의 행동 목록은 6종이다.
