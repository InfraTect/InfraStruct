# plan: resource-scanner

브랜치: `feat/resource-scanner-skeleton`

## 1. 목표 (무엇을)

**`ResourceScanner` 의 뼈대(스텁).** 공개 시그니처와 전용 예외 타입만 확정하고 본문은 비운다.

사용자가 `@Resource` 를 붙여 선언한 클래스를 reflection 으로 읽어 `ScannedResources` 로 바꾸는 것이
이 모듈의 최종 목표다. 파이프라인의 입구이고, 지금 이것이 없어서 뒤 단계 모듈의 test 가 전부 입력을
손으로 지어내고 있다. 이 문서는 최종 형태까지의 설계를 모두 담되, **이번 PR 의 산출물은 그중 뼈대까지**다.

`ScannedResources` 의 Javadoc 이 이미 `ResourceScanner.scan()` 을 반환 타입 계약으로 못 박아 두었으므로,
클래스 이름과 메서드 이름은 이번에 새로 정하는 것이 아니라 **이미 정해진 것을 따르는 것**이다.

`InfraStruct` · `CurrentStateStore` · `DesiredStateCreator` 뼈대와 같은 성격이다. 시그니처를 먼저
못 박아 호출부를 언블록하고, 본문은 뒤따르는 PR 에서 채운다.

### 1-1. PR 분할

한 PR 에 몰면 1,691줄이라 리뷰가 불가능하다. 세 개로 나눈다.

| PR | 범위 | 산출물 |
|---|---|---|
| **1 (이번)** | 뼈대 | plan / spec / summary, `ResourceScanner` 시그니처, `ResourceScanException`, 시그니처 test |
| 2 | 발견과 검증 | classgraph 수집, FQCN 정렬, logicalId 검증, 인스턴스화, `kind` 추출, 중복 검사 + `fixture/scan/bad/**` |
| 3 | 필드와 참조 추출 | 필드 순회, config / dependencies / requiredFields, 매크로 annotation 포착 + `fixture/scan/good/**` + SpotBugs 예외 목록 |

2번과 3번 브랜치는 직전 브랜치가 아니라 **merge 된 `dev` 에서 딴다.** 이어서 파면 PR diff 에 앞
단계 commit 이 다시 딸려 들어가 분할한 의미가 없어진다.

구현 전체를 미리 써 본 작업본은 `resource-scanner-wip` 브랜치(`d609a97`)에 남겨 두었다. 설계 근거가
실제로 도는 코드에서 나온 것이라는 뜻이고, 2번과 3번은 그것을 대조군 삼아 다시 유도한다.

## 2. 위치 — `com.infrastruct.internal`

클래스 다이어그램에서 `ResourceScanner` 박스는 **채우기 색이 없다.** 범례상 무색은
"프레임워크 내부 코드(프로바이더는 사용하지 않는 것)"다. 프로바이더가 상속하거나 구현하는
`Validator`(노란색)와 명확히 구분되어 있다.

코드 쪽 선례도 같은 답을 준다. 상태를 담는 **그릇**(`ScannedResources`, `ScannedResourceState`)만
`spi` 에 있고, 그 그릇을 만드는 **엔진 모듈**은 `internal` 에 둔다. `Comparator` 가 세운 선례다.

> ⚠️ **같은 패키지에 `com.infrastruct.internal.Comparator` 가 있다.** 정렬에 `Comparator.comparing` 을
> 쓰려면 반드시 `java.util.Comparator.comparing` 으로 full qualify 해야 한다. simple name 은 같은
> 패키지의 엔진 클래스로 잡혀서 컴파일이 깨진다.

## 3. 공개 시그니처

```java
public final class ResourceScanner {

    public ResourceScanner();                   // classpath 전체 스캔
    public ResourceScanner(String basePackage); // 특정 package 만 스캔

    public ScannedResources scan();
}
```

다이어그램의 `ResourceScanner` 박스도 `+ scan(): ScannedResources` 하나뿐이다.
`Comparator` 와 `DesiredStateCreator` 가 `final` 이므로 맞춘다.

`basePackage` 생성자가 필요한 이유는 두 가지다. 하나는 test 에서 정상 fixture 와 깨진 fixture 를
서로 다른 package 에 격리해 두고 골라 스캔하기 위해서고, 다른 하나는 나중에
`InfraStruct.run(mainClass)` 가 `mainClass.getPackageName()` 을 넘겨 사용자 코드만 스캔하게 하기
위해서다. 후자는 이번 범위가 아니지만 시그니처는 지금 열어 둔다.

## 4. MVP 를 어떻게 쓸 것인가

MVP-2 레포에 191줄짜리 `ResourceScanner` 가 있고 동작한다. 하지만 **MVP 는 개념
가능성을 확인한 버려진 코드고, 이 repo 가 최종까지 가는 쪽이다.** 그대로 옮기지 않는다.

이 구분으로 나눠서 쓴다.

| MVP-2 에서 **가져올 것** | 이유 |
|---|---|
| 스캔 알고리즘의 뼈대 (classgraph → 인스턴스화 → 필드 순회 → annotation 포착) | 실제로 돌려 본 순서 |
| 의존성을 필드 **값**(`Class` 객체)으로 읽는 방식 | 다이어그램 예시가 컴파일되지 않는 문제를 푼 유일한 형태 (§5) |
| 결정론 확보 (클래스명 정렬, annotation 정렬) | 없으면 CI 에서만 깨지는 test 가 생김 |
| 메타 필드(`kind`, `provider`) 제외, 자식 필드 우선 | 근거가 분명함 |

| MVP-2 에서 **버릴 것** | 이유 |
|---|---|
| 가변 상태 클래스에 직접 대입 | 현재 상태 클래스는 불변 (§6) |
| `kind` 가 null 이면 그냥 넘어가기 | 오류가 파이프라인 뒤에서 터짐 (§7-D) |
| logicalId 중복 방치 | 같음 (§7-E) |
| 모든 오류를 `IllegalStateException` 으로 | 배포되는 프레임워크에선 타입으로 잡을 수 없다 (§9) |
| `public class` (상속 열림) | `final` 로 닫는다 |

## 5. 설계 문서에서 확정된 것

### 의존성은 필드 **값**으로 표현한다

클래스 다이어그램의 자원 예시는 이렇게 되어 있다.

```java
public class AwsEc2 { Kind kind = AwsKind.Ec2; @Required public Vpc vpc; }

@Resource(name = "myEc2") public class MyEc2 extends AwsEc2 { { vpc = myVpc.class } }
```

**이 예시는 그대로는 컴파일되지 않는다.** 필드 타입이 `Vpc` 인데 대입하는 값은 `Class` 객체다.
`Class<? extends Vpc> vpc` 여야 한다. MVP-2 가 그 형태로 고쳐서 돌렸다.

```java
@Required public Class<? extends ScanVpc> vpc;   // 선언
{ vpc = GoodVpc.class; }                          // 사용자가 값을 넣는 자리
```

의도(값으로 `.class` 를 넣는다)는 다이어그램대로 가고, 타입만 컴파일되는 형태로 고친다.

> 📌 이 오류가 이미 repo 로 전파됐다. `spi/Required.java` 의 Javadoc 예시 `@Required public Vpc vpc;`
> 가 다이어그램을 그대로 베낀 것이다. 남의 파일이라 이번엔 손대지 않고 §12 에 올린다.

### 컬렉션 참조는 지원해야 한다

「종속 리소스」 문서가 RDS 를 이렇게 정의한다.

- 선행 필수: **Subnet 2개 이상** (서로 다른 AZ)
- `subnetClasses` **배열**로 DB Subnet Group 을 만든다

그래서 `List<Class<? extends Subnet>> subnetClasses` 같은 필드를 풀어서 각 원소를 의존성으로
등록해야 한다. 앞선 초안에서 "이번엔 config 로 흘려보낸다"고 적었던 것은 RDS 를 지원 불가로
만드는 결정이라 철회한다.

### 자원 하나가 참조하는 종류가 여럿이다

같은 문서의 EC2 표 기준으로 AMI, Subnet, Security Group, Key Pair, IAM Instance Profile 이
전부 선행 필수 또는 조건부 선행이다. 「어노테이션 설계」의 executor 의사코드도 필드별로 푼다.

```java
String subnetId = ctx.resolve(spec.subnetClass());
String sgId     = ctx.resolve(spec.sgClass());
```

logicalId 에서 실제 클라우드 ID 를 찾는 것은 이미 된다. `CurrentResourceState.physicalId` 가 있다.
문제는 그 앞이다. 어느 필드가 어느 자원을 가리켰는지가 필요하다. §7-C 로 이어진다.

## 6. 상태 클래스가 불변이라 생기는 제약

MVP-2 의 `ScannedResourceState` 는 가변이라 `state.config.put(...)` 으로 채웠다. 현재는 불변에
전인자 생성자다. 지역 변수에 모았다가 마지막에 한 번 생성한다.

### 🔴 `Map.copyOf` 는 null 값을 거부한다

MVP-2 는 값이 `null` 인 필드도 일부러 `config` 에 넣었다. "Validator 가 @Required 로 잡아야 하므로"
라는 주석까지 달려 있다. 그런데 현재 `ResourceState` 생성자는 `Map.copyOf(config)` 를 쓴다.
JDK 21 에서 확인했다.

```
Map.copyOf: NPE -> null 값 거부
List.copyOf: NPE -> null 거부
```

**null 값 필드는 `config` 에 넣지 않는다.** Validator 의 용도는 죽지 않는다. `requiredFields` 에는
이름이 그대로 들어가므로, Validator 는 `requiredFields` 중 `config` 에도 `dependencies` 에도 없는
이름을 찾으면 "필수 필드가 비었다"를 그대로 판정한다. 오히려 "없음"이 키 부재로 표현되어 더 명확하다.

`ResourceState` 를 고쳐 null 을 허용하는 선택지도 있으나, `resource-state-classes` plan §1 이 세운
규율("이미 `dev` 에서 돌고 있는 코드가 진실이고, 뒤늦게 올라가는 쪽이 맞춘다")에 어긋난다.

## 7. 설계 결정

### A. 스캔 순서를 고정한다

classpath 스캔 순서는 환경에 따라 달라진다. 클래스는 `Class::getName` 으로, captured annotation 은
annotation type 이름으로 정렬한다. 없으면 결과 순서가 머신마다 달라져 CI 에서만 깨지는 test 가 나온다.

### B. 메타 필드 `kind`, `provider` 는 config 에서 제외한다

자원의 설정값이 아니라 자원을 식별하는 메타 정보다. `config` 에 섞이면 Comparator 가
"종류가 바뀌었다"를 설정 변경으로 오인한다.

### C. 🔶 `dependencies` 는 미결이다 (초안에서 판단 변경)

앞선 초안은 "현재 타입(`List<String>`)에 맞추고 넘어간다"였다. 설계 문서를 읽고 **미결로 되돌린다.**

다이어그램에서 `DependencyDiff` 는 `+ field: type` 이다. 타입 자리가 `type` 이라는 자리표시자
그대로 비어 있다. 바로 옆 `FieldDiff` 는 `+ field: String` 으로 채워져 있다.
`ResourceState` 도 `+ dependencies: List` 로만 그려져 있고 원소 타입이 없다.

즉 `List<String>` 은 팀이 검토해서 고른 값이 아니라, 다이어그램에서 비어 있던 칸을 PR #19 가
구현하면서 메운 값이다. 그리고 §5 에서 본 대로 그 값으로는 EC2 의 참조 5종과 RDS 의
`subnetClasses` 배열을 표현할 수 없다.

**결론을 이 feature 안에서 내지 않는다.** `spi` 계약이고 `Comparator`(현서 님 파일)에 걸린다.
대신 아래처럼 짜서 결정이 늦어져도 스캐너가 막히지 않게 한다.

- 의존성 추출을 **메서드 하나로 격리한다.** 필드 값 하나를 받아 참조 목록을 돌려주는 형태.
- 그 메서드가 내부적으로는 `필드명 → logicalId 목록` 을 만들어 두고, 마지막에 현재 타입에 맞춰
  logicalId 만 평탄화해서 넘긴다.
- 타입이 바뀌면 **평탄화하는 한 줄과 그 test 만** 고치면 된다.

정보를 만들어는 두고 계약이 좁아서 버리는 상태로 두는 것이다. 낭비처럼 보이지만, 21일 마감과
미결 결정을 동시에 안고 가는 가장 싼 방법이다.

### D. `ProviderResource` 미상속과 `kind` null 을 거부한다

MVP-2 는 `instanceof ProviderResource` 일 때만 `kind` 를 읽고 아니면 null 로 두고 넘어갔다.
그러면 kind 가 null 인 상태가 파이프라인을 타고 흘러가서 한참 뒤에 터진다. 스캔 시점에 던진다.

### E. logicalId 중복을 스캔 시점에 잡는다

`Comparator.indexByLogicalId` 가 중복에 예외를 던지지만 그건 비교 단계다. 서로 다른 두 클래스가
같은 `@Resource(name)` 을 쓴 것은 스캔 시점에 이미 알 수 있다. 에러 메시지에 충돌한 두 클래스
이름을 모두 담는다.

### F. 상속 계층을 끝까지 올라가고 자식 필드가 우선한다

`MyEc2` → `AwsEc2` → `AwsResource` → `ProviderResource` 처럼 여러 단계를 상속한다.
`getDeclaredFields()` 는 자기 것만 주므로 `getSuperclass()` 로 `Object` 직전까지 올라간다.
자식과 부모가 같은 이름을 선언했으면(field shadowing) 자식이 이긴다. 자식부터 순회하면서 이미
담은 이름을 건너뛰는 방식으로 처리한다. `static` 과 synthetic 필드는 건너뛴다.

## 8. 스캔 알고리즘

```
scan()
  classgraph 로 @Resource 붙은 클래스 수집 (basePackage 있으면 그 범위만)
  클래스 이름순 정렬                                    → §7-A
  각 클래스마다 scanOne()
  logicalId 중복 검사                                   → §7-E
  ScannedResources(List) 생성

scanOne(type)
  logicalId  = @Resource(name) 읽고 검증                → §9
  instance   = 인자 없는 생성자로 생성 (initializer block 이 여기서 실행됨)
  ProviderResource 인지 확인, kind 추출, null 이면 거부  → §7-D
  필드 순회 (자식 → 부모)                                → §7-F
      static / synthetic  → skip
      kind / provider     → skip                        → §7-B
      이미 담은 이름       → skip (자식 우선)
      @Required 있으면    → requiredFields 에 이름 추가
      referencesOf(값)    → 참조 목록 (격리된 메서드)     → §7-C
          값이 Class 이고 @Resource 있음  → [그 name]
          값이 Collection/배열이고 원소가 위 조건 → [원소들의 name]
          그 외                          → []
      참조 목록이 비어 있지 않으면 → dependencies 에 추가
      비어 있고 값이 null           → 아무 데도 안 넣음   → §6 🔴
      비어 있고 값이 있음           → config 에 이름=값
  매크로 annotation 수집 (@Behavior 달린 것만, @Resource 제외)
  annotation type 이름순 정렬                            → §7-A
  ScannedResourceState 를 한 번에 생성 (불변)
```

## 9. 에러 계약 — MVP-2 와 다르게 간다

MVP-2 는 전부 `IllegalStateException` 이었다. 버려질 코드였으니 그걸로 충분했다.
이 repo 는 Maven 에 배포되어 남의 프로바이더와 남의 애플리케이션이 위에 서는 쪽이다.
**타입으로 잡을 수 없는 예외는 프레임워크의 결함이다.**

전용 예외 하나를 두고 그 아래에 사유를 담는다.

```java
public class ResourceScanException extends RuntimeException { ... }
```

`RuntimeException` 을 고르는 이유는 이것이 사용자의 **선언 실수**라서다. 복구 가능한 조건이
아니라 고쳐야 할 코드이므로 checked 로 강제해도 호출부가 할 수 있는 일이 없다.

| 조건 | 비고 |
|---|---|
| `@Resource(name)` 이 비었거나 공백뿐 | logicalId 는 state 파일의 키라 비면 안 됨 |
| `@Resource(name)` 에 공백 문자가 섞임 | 렌더링과 state 파일 키가 깨짐 |
| logicalId 가 다른 클래스와 중복 | §7-E. 충돌한 두 클래스를 모두 표시 |
| `ProviderResource` 미상속 | §7-D |
| 인스턴스화 후에도 `kind` 가 null | §7-D |
| 인자 없는 생성자가 없어 인스턴스화 실패 | 원인 예외를 cause 로 |
| 필드 값을 읽지 못함 | `IllegalAccessException` 을 cause 로 |

메시지에는 문제가 된 클래스의 FQCN 을 반드시 담는다. 사용자가 자기 코드 어디를 고쳐야 하는지
바로 알아야 한다.

> 예외 클래스를 어느 package 에 둘지는 §12 에 올린다. 사용자가 catch 할 수 있어야 하면 `api`,
> 엔진이 삼키고 끝낼 거면 `internal` 이다. 지금은 `internal` 로 두고 논의한다.

## 10. Test fixture 설계

framework 는 프로바이더를 의존하지 않으므로 `AwsVpc` 같은 것을 쓸 수 없다. test 전용 자원 계층을
직접 만든다. **쓸 수 없다는 것이 아니라 쓰지 않아도 된다는 것 자체가 이 설계의 주장**이다.

`framework/src/test/java/com/infrastruct/fixture/scan/` 아래:

```
scan/
├── ScanKind.java        enum ScanKind implements Kind { Vpc, Subnet, Ec2, Rds }
├── ScanProvider.java    class ScanProvider extends Provider {}
├── ScanResource.java    ProviderResource 상속. provider 설정 + 조부모 필드 검증용 owner 필드
├── ScanVpc / ScanSubnet / ScanEc2    단일 참조와 스칼라 필드
├── ScanRds.java         List<Class<? extends ScanSubnet>> 컬렉션 참조 (§5)
├── Tagged.java          @Behavior 달린 매크로 annotation (멤버 있음)
├── Encrypted.java       @Behavior 달린 매크로 annotation (멤버 없음)
├── Plain.java           @Behavior 없는 평범한 annotation. 포착되면 안 됨
├── TagHandler / EncryptHandler       BehaviorHandler 구현
├── good/                정상 자원. 대부분의 test 가 이 package 만 스캔
└── bad/                 깨진 자원. 하위 package 로 하나씩 격리
    ├── blank/           name 이 빈 문자열
    ├── whitespace/      name 이 공백뿐
    ├── inner/           name 중간에 공백
    ├── noctor/          인자 없는 생성자 없음
    ├── notprovider/     ProviderResource 미상속
    ├── nokind/          kind 를 안 채움
    └── dup/             logicalId 중복 (클래스 2개)
```

깨진 자원을 **하나씩 다른 package 로 격리**하는 것이 핵심이다. 한 package 에 모아 두면 하나를
스캔할 때 다른 것도 같이 걸려서 어느 검증이 예외를 냈는지 구분할 수 없다.

`Plain` annotation 을 두는 이유는 "`@Behavior` 가 달린 것만 포착한다"를 반증 가능하게 만들기
위해서다. 매크로 annotation 만 있으면 전부 포착하는 구현도 test 를 통과해 버린다.

`ScanRds` 는 컬렉션 참조 지원의 반증 장치다. 없으면 단일 참조만 처리하는 구현이 그냥 통과한다.

## 11. 구현 순서 (red → green)

spec.md 의 행동 목록이 될 순서다. §1-1 의 PR 경계를 함께 표시한다.

**1번 PR (이번, 뼈대)**

1. 인자 없이, 그리고 basePackage 로 인스턴스를 만들 수 있다
2. `scan()` 이 빈 `ScannedResources` 를 돌려준다 (스텁)
3. `ResourceScanException` 이 메시지와 cause 를 보존하는 `RuntimeException` 이다

**2번 PR (발견과 검증)**

4. `@Resource` 가 붙은 클래스를 모두 찾고 logicalId 는 `name()` 값 그대로다
5. basePackage 밖의 자원은 스캔하지 않는다
6. 결과 순서가 클래스 이름순으로 고정된다
7. `kind` 를 `ProviderResource` 에서 읽는다
8. 에러 6종이 각각 `ResourceScanException` 을 던지고 메시지에 FQCN 이 담긴다

**3번 PR (필드와 참조 추출)**

9. `kind` 와 `provider` 는 config 에도 dependencies 에도 들어가지 않는다
10. 스칼라 필드가 config 에 이름과 값으로 들어간다
11. 조부모 클래스의 필드까지 읽는다
12. 자식이 shadowing 한 필드는 자식 값이 이긴다
13. 값이 `Class` 이고 `@Resource` 가 있으면 dependencies 에 그 name 이 들어간다
14. 컬렉션 참조 필드의 원소가 각각 dependencies 에 들어간다
15. 값이 null 인 필드는 config 에 들어가지 않는다
16. `@Required` 필드 이름이 requiredFields 에 모인다. 참조 필드도 포함된다
17. `@Behavior` 가 달린 annotation 만 포착하고 `@Resource` 는 제외한다
18. 포착된 annotation 은 실제 붙어 있던 인스턴스이고 멤버 값을 읽을 수 있다
19. 포착 순서가 annotation type 이름순으로 고정된다
20. `@Behavior` 없는 annotation 은 포착되지 않는다

## 12. 범위 밖 (그리고 왜)

- **`scan()` 본문** — §1-1 의 2번과 3번 PR. 이번 PR 은 시그니처만 못 박는다.
- **Test fixture (`fixture/scan/**`)** — 본문이 없으면 검증할 대상이 없다. 각 fixture 는 그것을
  실제로 쓰는 PR 과 함께 올린다. §10 의 설계는 그대로 간다.
- **SpotBugs 예외 목록 (`config/spotbugs/exclude.xml`)** — `setAccessible` 억제가 목적이라
  reflection 코드가 들어오는 3번 PR 에서 함께 올린다. §14 참조.
- **`InfraStruct.run()` 배선** — `PlanCreator` 가 PR #25 에 묶여 있어 파이프라인 전체를 엮을 수 없다.
  WBS 상 이것은 8/22 부터 정연 님 몫이다.
- **`DesiredStateCreator` 본문 채우기** — 스캐너 출력이 그 입력이지만 남의 파일이다.
- **provider 별 필터링** — `@InfraStructApplication(provider="aws")` 와 다른 프로바이더의 자원이
  섞였을 때 걸러 낼지는 Validator 의 판단으로 미룬다. 스캐너는 발견한 것을 전부 넘긴다.
- **`spi/Required.java` Javadoc 예시 수정** (§5) — 남의 파일.
- **`dependencies` 타입 변경** (§7-C) — `spi` 계약이고 `Comparator` 에 걸린다.

## 13. 열린 질문

1. **`dependencies` 에 필드명을 남길 것인가** (§7-C). 설계 문서 기준으로는 남겨야 한다.
   `spi` 변경이라 팀 결정이 필요하고, 이 feature 는 §7-C 의 격리 구조로 결정을 기다린다.
2. **`ResourceScanException` 을 `api` 에 둘 것인가 `internal` 에 둘 것인가** (§9).
   사용자가 catch 할 수 있어야 하는지에 달렸다.
3. **`Required` Javadoc 과 다이어그램의 자원 예시를 누가 언제 고칠 것인가** (§5).
   지금 상태로는 컴파일되지 않는 예시라 프로바이더 개발자가 그대로 따라 하면 막힌다.

## 14. 예상 리스크

- **JPMS.** `setAccessible(true)` 로 사용자 클래스의 필드를 읽는다. 사용자 코드가 named module 이면
  해당 package 를 `opens` 하지 않는 한 실패한다. MVP 는 마주칠 일이 없던 문제지만, 배포되는
  프레임워크에는 실제로 걸린다. 이번엔 classpath 사용을 전제로 두되, plan.md §5 의
  "internal 격리 강화(JPMS)" 항목과 함께 다뤄야 한다.
- **SpotBugs** 가 `setAccessible` 에 `DP_DO_INSIDE_DO_PRIVILEGED` 를 낼 수 있다. reflection 스캐너의
  본질적 동작이므로 `@SuppressFBWarnings` 로 사유를 적어 억제한다. `ProviderResource` 가 이미
  같은 방식으로 국소 예외를 둔 선례가 있다.
- **Checkstyle** 은 generic type parameter 를 대문자 한 글자로만 허용한다.
- **`java.util.Comparator` 이름 충돌** (§2). 컴파일 에러로 바로 드러나므로 위험도는 낮다.
