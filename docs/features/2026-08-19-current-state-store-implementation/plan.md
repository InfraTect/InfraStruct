# feature: current-state-store-implementation

브랜치: `feat/CurrentStateStore-impl-v2`

## 1. 목표 (무엇을)

**`CurrentStateStore` 의 본문을 실제로 채운다.** 지금은 뼈대다 —
`load()` 는 항상 빈 값을 돌려주고 `save()` 는 아무것도 하지 않는다
(`2026-08-14-current-state-store/summary.md`).

이번 feature 의 산출물은 **save → load 왕복이 실제로 성립하는 파일 기반 상태 저장소**다.

```
CurrentResources ──save()──▶ JSON 파일 ──load()──▶ CurrentResources
                                (동일한 내용이어야 한다)
```

> Terraform 비유: `terraform.tfstate` 를 읽고 쓰는 부분. 이 파일이 있어야
> "지금 뭐가 떠 있는지"를 알고 diff 를 낼 수 있다. 다만 **diff 를 내는 것은
> `Comparator` 의 몫**이고, 이번 범위는 **저장/복원까지**다.

## 2. 왜 지금인가 — 막고 있던 설계 숙제가 풀렸다

스켈레톤 때 본문을 비운 이유는 두 가지 미해결 설계였다
(`2026-08-14-current-state-store/plan.md` §4, `resource-state-classes/summary.md` §7).

| 숙제 | 당시 문제 | 이번 해법 (§4에서 상술) |
|---|---|---|
| `kind` 가 인터페이스 | Gson 역직렬화가 `Interfaces can't be instantiated!` 로 깨짐 | 상태 파일에 **enum 클래스 FQN + `value()`** 를 함께 적고, 읽을 때 리플렉션으로 상수를 되찾는다 |
| 상태 클래스가 불변 | Gson 이 `Unsafe` 로 만들며 생성자의 `copyOf` 정규화를 건너뜀 | **Gson 에게 상태 클래스를 직접 맡기지 않는다.** 파일 스키마 전용 DTO(record)만 Gson 이 다루고, DTO → 상태 클래스 변환은 우리가 생성자를 호출해서 한다 |

핵심은 두 번째다. **`Unsafe` 를 막는 가장 확실한 방법은 Gson 에게 상태 클래스를
보여주지 않는 것**이다. `InstanceCreator` 를 등록하는 방법도 있지만, 그것은
"Gson 이 필드를 리플렉션으로 채우는" 경로를 그대로 두고 인스턴스 생성만 가로채는
것이라 `final` 필드 정규화 문제가 반만 해결된다.

## 3. 상태 파일 스키마 (무엇을 저장하나)

```json
{
  "version": 1,
  "resources": [
    {
      "kindType": "com.example.aws.AwsKind",
      "kindValue": "EC2",
      "logicalId": "web-server",
      "physicalId": "i-0abc123",
      "config": { "instanceType": "t3.micro", "port": 22 },
      "dependencies": ["main-vpc"]
    }
  ]
}
```

- **`version`** — 지금은 항상 `1`. 나중에 스키마가 바뀌었을 때 "옛날 파일"을
  알아볼 수 있게 처음부터 넣어 둔다. 나중에 넣으면 버전 없는 파일과 구분할 방법이 없다.
- **`requiredFields` 는 저장하지 않는다.** `CurrentResourceState` 에는 "필수 여부"라는
  개념 자체가 없어 항상 비어 있다(`ResourceState#requiredFields()` Javadoc). 읽을 때
  `Set.of()` 로 채운다.
- **`physicalId` 는 `null` 가능** — apply 전에는 클라우드가 식별자를 안 준다.
  `null` 이면 JSON 에서 키가 아예 빠진다(Gson 기본 동작).

## 4. 설계 판단 (전부 사용자 검토를 거쳐 확정됨 — ✅)

> ⚠️ **이 절의 결정 중 둘은 이 feature 를 넘어서는 전역 규칙이다** — **예외 처리 스킴**(§4.4)과
> **상태 왕복 타입 제약**(§4.1 · §4.5). 둘은 `CONVENTIONS.md` §8 · §9 로 승격했고,
> **별도 브랜치 `docs/exception-and-type-conventions`(커밋 `22b0b9e`)에서 PR 로 분리**했다.
> 구현 PR 에 섞이면 "코드 리뷰하다 딸려온 문서"가 되어 규칙 자체를 제대로 논의할 수 없기 때문이다.
>
> **이 브랜치에는 그 문서가 없다.** 그래서 §4.6 에 두 스킴을 규칙 형태로 요약해 이 plan 만 읽어도
> 구현할 수 있게 해 둔다. 컨벤션 PR 이 머지되면 `CONVENTIONS.md` §8 · §9 가 **정본**이 되고,
> 이 plan 은 "왜 그렇게 정했나"의 근거 기록으로 남는다.

### 4.1 ✅ `kind` 를 어떻게 되살릴 것인가 — **A안 확정**

`Kind` 는 인터페이스이고 구현체는 프로바이더의 enum 이다. JSON 에 남길 수 있는 것은
문자열뿐이라, 읽을 때 "어느 enum 의 어느 상수였는지"를 복원할 근거가 필요하다.

| 안 | 저장 형태 | 복원 방법 | 문제 |
|---|---|---|---|
| **A. FQN + value (추천)** | `"com.example.AwsKind"` + `"EC2"` | 클래스를 로드해 상수 중 `value()` 가 일치하는 것을 찾는다 | 프로바이더가 enum 클래스를 옮기면 옛 상태 파일을 못 읽는다 |
| B. providerId + value | `"aws"` + `"EC2"` | `ModuleRegistry` 로 provider → Kind enum 을 찾는다 | **`ModuleRegistry` 가 아직 없다** → 이번엔 불가능 |
| C. value 만 | `"EC2"` | 값만 든 내부 `Kind` 구현체를 만들어 넣는다 | 복원된 kind 가 원래 enum 상수와 `equals` 하지 않다 → Comparator 가 값 비교를 하도록 강제하게 됨 |

**A 를 추천한다.** B 는 의존성이 없어 못 하고, C 는 지금 정할 이유가 없는
Comparator 의 비교 방식(동일성이냐 값이냐)을 상태 저장소가 앞질러 결정해 버린다.
A 는 복원된 것이 **원래 enum 상수 그 자체**라 뒤 단계에 아무 제약도 남기지 않는다.

**결정을 가른 근거는 `Applier` 다.** `ResourceChange` 계약상 DELETE 는 `after` 가 `null` 이라,
프로바이더가 "무엇을 지워야 하는지" 아는 유일한 출처가 **상태 파일에서 읽어 온 `before`** 다.
프로바이더는 그 kind 를 자기 enum 으로 캐스팅해 분기할 수밖에 없다:

```java
switch ((AwsKind) change.before().kind()) {
    case EC2 -> ec2.terminateInstances(change.before().physicalId());
    ...
}
```

C 안이면 이 캐스팅이 **자원을 삭제하는 가장 위험한 경로에서** `ClassCastException` 으로 죽는다.
C 를 택하려면 "프로바이더는 kind 를 캐스팅하지 말고 `value()` 문자열로만 분기하라"는 제약을
상태 저장소가 프로바이더 작성법에 강요하게 된다. A 는 복원된 것이 원본 enum 상수 그 자체라
뒤 단계에 아무 제약도 남기지 않는다.

**딸려오는 요구사항: `Kind` 구현체는 enum 이어야 한다.** A 는 "클래스를 로드해 enum 상수를
뒤진다"는 방식이라 이것을 전제한다. 지금까지 이건 `Kind` Javadoc 의 *관례*였지만
(*"프로바이더가 자신의 자원 목록을 enum 으로 구현한다"*), 이번 feature 가 이를
**요구사항으로 승격**시킨다 — enum 이어야 자원 종류가 닫힌 집합이 되므로 원래 의도와도 맞는다.
enum 이 아닌 `Kind` 구현체를 만나면 조용히 넘어가지 않고 명확한 예외를 던지고, 이 사실을
`Kind` 의 Javadoc 에 명시한다.

**상수를 찾는 기준은 `value()` 다** (`name()` 이 아니다). `value()` 가 `Kind` 인터페이스가
약속한 유일한 식별자이기 때문이다. enum 상수 이름은 프로바이더가 언제든 바꿀 수 있는 내부
사정이지만 `value()` 는 계약이다. 같은 `value()` 를 가진 상수가 둘 이상이면 어느 쪽을 골라도
틀릴 수 있으므로 예외를 던진다.

A 의 약점(클래스 이름 결합)은 인정하고 문서에 남긴다. `ModuleRegistry` 가 생기면
`kindType` 을 `providerId` 로 바꾸는 것이 자연스러운 다음 수순이고, 그때 `version` 을
2 로 올리면 된다 — §3 에서 `version` 을 미리 넣어 두는 이유가 이것이다.

### 4.2 ✅ 상태 파일의 위치와 생성자 — **경로 필수 확정**

지금 생성자는 인자가 없다(`new CurrentStateStore()`). 파일을 쓰려면 경로가 필요하다.
이 결정은 원래 이번으로 미뤄져 있었다 — 직전 summary: *"생성자에 provider/경로 주입은
그 설계가 선 뒤에 함께 정한다(지금은 인자 없는 생성자)"*. 실제로 이 타입을 생성하는 곳은
`CurrentStateStoreTest` 뿐이라 호환성 부담도 없다.

**결정: 경로를 받는 생성자 하나만 둔다. 무인자 생성자는 없앤다.**

```java
public final class CurrentStateStore {
    /** 상태 파일의 관례적 이름. 경로 조합은 호출부(파이프라인)의 몫이다. */
    public static final String DEFAULT_FILE_NAME = "infras.state.json";

    public CurrentStateStore(Path stateFile) { ... }
}
```

**왜 무인자 생성자를 없애는가.** 지금 테스트 3개가 전부 `new CurrentStateStore()` 를 쓴다.
스텁일 땐 무해하지만 본문이 채워지는 순간 그 코드는 **작업 디렉터리(= 레포 루트)** 에서
파일을 찾고, save 테스트는 **레포 루트에 상태 파일을 만들어 놓는다.** 테스트끼리 서로의
파일을 보고, `git status` 가 더러워지고, CI 와 로컬이 다르게 돈다. 무인자 생성자를 남기면
이 함정이 계속 열려 있다. 없애면 테스트가 경로를 안 줄 수 없어져 `@TempDir` 을 쓸 수밖에
없다 — 관례가 아니라 **컴파일러가 강제**한다.

설계상으로도 이쪽이 맞다. **"상태 파일이 어디 있어야 하는가"는 파이프라인의 정책이지
저장소의 정책이 아니다.** 저장소가 할 일은 "이 파일을 읽고 쓴다" 하나다. Terraform 으로 치면
`-state=` 를 해석하는 건 CLI 이고 백엔드는 주어진 위치만 다루는 것과 같다. 그래서 파일
**이름**만 상수로 노출하고 경로 조합은 호출부에 맡긴다.

**파일 이름/위치**: `infras.state.json`, 작업 디렉터리 바로 아래 — 눈에 보이는 자리.
Terraform 이 `terraform.tfstate` 를 숨기지 않는 것과 같은 이유다. 상태 파일은 사용자가
존재를 알아야 하는 물건이다(실수로 지우면 안 되고, `.gitignore` 에 넣을지 판단해야 한다).
`.infrastruct/` 같은 숨김 폴더는 Terraform 의 `.terraform/` — 프로바이더 캐시처럼 사용자가
몰라도 되는 내부 물건 — 에 어울리는 자리다.

### 4.3 ✅ `save()` 의 반환형 — **`void` + 예외 확정**

drawio 는 `save(CurrentResources): Boolean`, 이미 머지된 스켈레톤은 `void save(...)` 다.
**`void` 를 유지하고 실패는 예외로 터뜨린다.**

**`Boolean` 은 무시할 수 있다.** Java 는 반환값을 안 받아도 경고조차 하지 않는다
(`store.save(current);` 는 그냥 컴파일된다). 그리고 이 실패가 묻히면 무슨 일이 벌어지는지가
결정적이다 — 저장이 실패한 채 프로세스가 끝나면 **다음 실행에서 프레임워크는 "아직 아무것도
안 만들었다"고 판단하고 이미 떠 있는 자원을 또 만든다.** IaC 에서 가장 나쁜 실패 모드다.
중복 과금에, 프레임워크가 추적하지 못하는 고아 자원이 남아 사람이 콘솔에서 손으로 치워야 한다.
이런 실패는 **무시가 불가능해야** 한다. 예외는 안 잡으면 프로그램이 멈추지만 `Boolean` 은
안 보면 지나간다.

덧붙여 `Boolean` 은 **"왜" 를 못 담는다** — 권한 문제인지, 디스크가 찼는지, 경로가 틀렸는지를
`false` 하나로는 사용자가 고칠 수 없다. 그리고 호출부가 `if (!store.save(x)) throw ...;` 를
매번 쓸 거라면 그건 저장소가 처음부터 던졌어야 할 예외다.

> **drawio 반영**: 다이어그램의 `save(CurrentResources): Boolean` 은 `void` 로 고쳐야 한다.
> 팀 공유 문서라 이번 feature 에서 코드로 건드리지 않는다 — **다이어그램 수정은 사용자가 직접
> 진행하기로 했다.**

### 4.4 ✅ 실패했을 때 어떻게 행동하나 (이번 feature 의 핵심 계약)

| 상황 | 행동 | 왜 |
|---|---|---|
| 파일이 없다 (최초 실행) | **빈 `CurrentResources`** 반환 | 기존 계약 그대로. "아직 아무것도 안 만들었다"가 맞는 해석이다 |
| 파일이 있는데 JSON 이 깨졌다 | **예외를 던진다** | 빈 값으로 넘어가면 프레임워크가 자원을 전부 새로 만든다(§4.3 의 그 재앙). "파일 없음"과 "파일 깨짐"은 전혀 다른 사건인데 빈 값으로 뭉개면 같아진다 |
| `version` 이 `1` 이 아니다 | **예외를 던진다** | 해석할 줄 모르는 스키마를 추측해서 읽으면 안 된다 |
| `kindType` 클래스를 못 찾는다 / enum 이 아니다 / 맞는 상수가 없다 | **예외를 던진다** | 위와 같다. 프로바이더 jar 이 빠진 상태로 apply 하면 안 된다 |
| 읽기·쓰기 IO 실패 | **예외를 던진다**, 기존 파일은 **그대로 남는다** | 아래 참조 |

**쓰기는 원자적으로 한다.** 같은 폴더에 임시 파일을 쓰고 `Files.move(ATOMIC_MOVE)` 로
갈아끼운다. 파일에 직접 덮어쓰다 중간에 죽으면 **반쯤 쓰인 JSON** 이 남고, 그건 파일이 없는
것보다 나쁘다 — 다음 실행이 위 표 2행에 걸려 아무것도 못 하고 사용자가 손으로 복구해야 한다.
임시 파일 방식이면 죽어도 원본이 멀쩡히 남는다. 임시 파일을 **같은 폴더**에 만드는 이유는
다른 파일시스템 사이에서는 `ATOMIC_MOVE` 가 실패하기 때문이다.

**예외 타입: `com.infrastruct.internal.StateStoreException` 을 새로 만든다.**

```java
/** 상태 파일을 읽거나 쓰지 못했을 때 던진다. */
public class StateStoreException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public StateStoreException(String message) { ... }
    public StateStoreException(String message, Throwable cause) { ... }
}
```

**이 모양은 레포의 선례를 따른 것이다.** `feat/resource-scanner-skeleton` 브랜치의
`internal/ResourceScanException` 이 똑같은 형태다 — `internal` 배치, `RuntimeException` 상속,
`serialVersionUID`, 생성자 두 개(`message` / `message, cause`), 그리고 *"메시지에는 문제가 된
클래스의 FQCN 을 반드시 담는다"*. 이쪽은 FQCN 대신 **파일 경로**를 담는다.
**그쪽도 예외를 하나만 만들었다** — 스캔 실패의 원인이 여러 갈래(이름 비었음, 인스턴스화 실패,
리플렉션 오류)인데도 타입을 나누지 않고 메시지로 구분했다.

**예외냐 결과 객체냐**: 이 레포에는 두 관례가 공존하지만 역할이 다르다.

| 방식 | 언제 | 왜 | 사례 |
|---|---|---|---|
| 예외 | 더 진행이 불가능 | 첫 실패에서 멈춰야 하고, 뒤 단계로 넘길 값이 없다 | `ResourceScanException`, `Comparator` 의 `IllegalArgumentException` |
| 결과 객체 | 문제를 **여러 개 모아** 보고 | 위반 10개를 한 번에 보여줘야 사용자가 10번 고치지 않는다 | `Validator` → `ValidationResult`(drawio) |

상태 파일 읽기는 앞쪽이다. 파일이 깨졌으면 "깨진 항목 목록"을 모을 수조차 없다 —
JSON 파싱 자체가 안 되기 때문이다.

**누가 잡는가 — 현재는 아무도 안 잡는다.** 모든 원격 브랜치의 `src/main` 을 통틀어 `catch` 가
하나도 없다. 예외는 사용자 `main()` 까지 올라가 스택 트레이스로 끝난다. 최종 모습은 그러면
안 된다 — 프레임워크는 **경계에서 예외를 사람이 읽을 메시지로 바꿔야** 하고(Terraform 이
Go 스택 트레이스 대신 `Error: ...` 를 보여주듯), 그 경계는 사용자 코드와 엔진이 만나는 유일한
지점인 `InfraStruct.run()` 이다.

```
사용자 main()
    ↓
InfraStruct.run()          ← 잡아서 사용자용 메시지로 바꿀 자리 (아직 스텁)
    ↓
CurrentStateStore.load()   ← 그냥 던진다
```

**엔진 안에서 던지고, 경계에서 한 번 잡는다.** `CurrentStateStore` 가 자기 실패를 스스로
삼키면 안 된다(§4.3). 그리고 그 경계 코드를 쓰는 순간이 곧 "예외를 나눌지" 판단할 수 있는
시점이다 — 안내 문구가 갈라지는 지점이 타입이 갈라져야 할 지점이기 때문이다.

**저장소 안에서 `catch` 는 "변환"에만 쓴다.** 저수준 예외를 잡아 맥락(파일 경로 + 도메인
의미)을 붙여 다시 던진다. 문제를 사라지게 하는 `catch` 는 쓰지 않는다 — `load()` 는 그
자리에서 결정을 내릴 정보(사용자에게 어떻게 보여줄지, 중단할지, dry-run 인지)를 하나도 갖고
있지 않아, `catch` 안에서 할 수 있는 일이 실제로 없다. 이 레포는 Checkstyle
`EmptyCatchBlock`(`exceptionVariableName = expected|ignored`)으로 이미 "삼키기는 예외적인
일"임을 강제하고 있다.

```java
catch (NoSuchFileException ignored)  → 빈 CurrentResources 반환   // 유일하게 삼키는 자리
catch (IOException e)                → throw new StateStoreException("...: " + path, e)
catch (JsonSyntaxException e)        → throw new StateStoreException("...: " + path, e)
catch (ClassNotFoundException e)     → throw new StateStoreException("...: " + path, e)
```

첫 줄만 삼킨다. **"파일 없음"은 에러가 아니라 "아직 아무것도 apply 하지 않았다"는 도메인
사실**이고, 그 해석은 저장소만 내릴 수 있기 때문이다. 판단할 수 있는 것은 여기서 처리하고,
판단할 수 없는 것은 올려보낸다.

**경계(`InfraStruct.run()`)를 맡는 쪽에 전달할 보장 3가지** — summary 에 남긴다:

1. **`catch (StateStoreException e)` 절 하나면 충분하다.** 나중에 타입을 쪼개도 하위 타입이
   같이 잡히므로 그 코드는 그대로 동작한다 — 지금 하나로 두는 것이 경계 코드를 미리 묶지 않는다.
2. **`getMessage()` 를 그대로 사용자에게 보여줘도 된다.** 실패한 파일 경로가 이미 메시지에
   들어 있다(경로를 아는 것은 저장소뿐이므로 저장소가 넣는 것이 맞다).
3. **`cause` 가 보존된다.** verbose/debug 모드에서 원인 스택을 그대로 펼칠 수 있다.

- **`internal` 에 두는 것이 맞다.** 패키지 판별 규칙은 *"엔진 **밖의** 누군가가 이 타입을
  코드에서 직접 쥐는가"*(CONTRIBUTING §2)이고, 이 예외를 받는 쪽은 엔진 자신인
  `InfraStruct` 다. `InfraStruct` 는 `api` 패키지에 있지만 사용자가 아니라 **엔진 그 자체**이고,
  애초에 `CurrentStateStore`·`Comparator` 같은 internal 타입 7개를 필드로 들 예정이다.
- **`internal` 이라 오히려 안전하다.** 이 패키지의 계약이 *"언제든 자유롭게 리팩터링할 수 있다"*
  라, 나중에 예외를 쪼개거나 이름을 바꿔도 아무도 안 깨진다. `spi` 에 뒀다면 그 순간 세상의
  모든 프로바이더가 인질이 된다.
- **표준 예외 재사용보다 나은 이유**: Checkstyle `IllegalThrows` 가 `RuntimeException` 직접
  throw 를 막고 있어 어차피 전용 타입이나 표준 하위 타입을 골라야 한다. 전용 타입이면
  "상태 저장소에서 난 문제"가 이름에 드러난다.
- **하나만 만든다.** 의미상 "깨진 JSON"(사용자가 고칠 수 있음)과 "IO 실패"(환경 문제)는
  성격이 다르지만, **지금 이 예외를 잡는 코드가 없다**(`InfraStruct.run()` 이 스텁). 잡는 쪽이
  없는데 타입을 나누면 왜 나눴는지가 코드에 안 드러난다. 파이프라인이 배선되며
  "사용자에게 뭐라고 안내할지"가 정해질 때 쪼갠다 — 그때는 판단 근거가 생기고, internal 이라
  쪼개는 비용도 없다.
- **메시지에 파일 경로를 반드시 담는다.** 지금 단계에서 사용자 경험을 좌우하는 건 예외 타입
  이름이 아니라 `"상태 파일이 깨졌다: /path/to/infras.state.json"` 같은 메시지다.
  원인 예외는 `cause` 로 보존한다.

unchecked 로 가는 이유: `load`/`save` 시그니처에 `throws` 를 더하지 않기 위해서다.

### 4.5 ✅ `config` 값의 숫자 타입 — 정규화 + 알려진 한계

`config` 는 `Map<String, Object>` 다. 값의 Java 타입이 무엇이었는지는 **JSON 이 담지 못한다** —
JSON 의 숫자는 그냥 숫자이고 `int`/`long`/`double` 구분이 없다. 아무 처리도 안 하면 Gson 은
`Object` 자리의 숫자를 전부 `Double` 로 읽어, `22` 로 저장한 값이 `22.0` 으로 돌아온다.
그러면 Comparator 가 **바뀌지도 않은 값을 바뀌었다고** 본다.

**정규화 규칙 (읽을 때)**:

| JSON | 결과 |
|---|---|
| 소수부 없음 | `Long` |
| 소수부 있음 | `Double` |

**값의 크기로 `int`/`long` 을 추측하지 않는다.** JSON 에 그 구분이 없어 애초에 되살릴 수 없고,
추측은 같은 필드의 타입을 값에 따라 흔들리게 만든다. 정수는 무조건 `Long` 으로 통일한다.
Gson 은 `Long 22` 를 `22` 로, `Double 22.0` 을 `22.0` 으로 쓰므로 **정수와 실수는 파일 내용만으로
구분된다.**

**저장소가 보장하는 것 / 못 하는 것**을 분명히 해 둔다:

- 보장한다 — `save` → `load` 왕복이 **안정적**이다. 여러 번 반복해도 값이 계속 변하지 않는다.
- 보장한다 — `int 100` 으로 넣든 `long 100` 으로 넣든 **같은 객체(`Long 100`)로 돌아온다.**
- 보장 못 한다 — 선언 타입이 `int` 였는지 `long` 이었는지. 그 정보는 JSON 에 없다.

> **2026-08-20 개정.** 최초 설계는 "소수부 없음 + `int` 범위 → `Integer`" 였다. `config` 필드가
> 대부분 `int` 라는 이유였지만, 그 대가로 `long sizeGb = 100` 이 `Integer 100` 으로 돌아와
> `Comparator` 의 `Objects.equals` 가 **apply 해도 사라지지 않는 유령 diff** 를 만들었다.
> 정수를 `Long` 하나로 통일하면 `int`·`long` 양쪽이 모두 맞아떨어져 손해 보는 쪽이 없으므로
> 규칙을 바꿨다. 대신 **`DesiredStateCreator` 가 같은 정규 타입을 써야 한다**는 짝 규칙이 생겼다
> (§4.6-3). `CONVENTIONS.md` §9.3 도 같이 고쳤다.

### 4.6 전역 규칙으로 승격한 것 — 규칙 요약

위 절들이 "왜"라면 여기는 "무엇"이다. `CONVENTIONS.md` §8 · §9 에 들어간 내용과 같고,
그 문서가 아직 이 브랜치에 없으므로 구현이 참조할 수 있게 여기에도 둔다.

#### 스킴 ① 예외 처리 (→ `CONVENTIONS.md` §8)

1. **예외냐 결과 객체냐 — "모아서 보고할 게 있느냐"로 가른다.**
   더 진행 못 하면 예외, 문제를 여러 개 모아 보고해야 하면 결과 객체(검증이 그렇다).
   상태 파일 파싱 실패는 "깨진 항목 목록"을 모을 수조차 없으므로 예외다.
2. **unchecked 를 기본으로 한다.** 기준은 *"호출부가 잡아서 할 수 있는 일이 있는가"*.
   고쳐야 할 문제에 checked 를 강제하면 할 일 없는 `try/catch` 만 늘어난다.
3. **전용 예외는 `internal` 에, 이름은 `<모듈>Exception`.**
   생성자 두 개(`message` / `message, cause`) + `serialVersionUID`.
   **메시지에 사용자가 고쳐야 할 대상(FQCN·파일 경로)을 반드시 담는다.**
4. **모듈당 하나로 시작한다.** 원인이 여러 갈래여도 메시지와 `cause` 로 구분한다.
   쪼갤 시점은 `catch` 를 다르게 쓸 호출부가 실제로 생겼을 때다.
   (하나→여럿은 안 깨지고 여럿→하나는 깨진다 — 되돌릴 수 있는 방향에서 시작)
5. **`catch` 는 변환용이다.** 저수준 예외에 맥락을 붙여 **다시 던진다.**
   문제를 사라지게 하는 `catch` 는 쓰지 않는다. 판단 기준: *`catch` 안에서 실제로 내릴 결정이
   있는가.* 없으면 잡지 않는다.
6. **삼키는 것은 "에러가 아니라 도메인 사실"일 때만** (`NoSuchFileException` → 최초 실행).
   이때 변수명은 `ignored`/`expected` — Checkstyle 이 강제한다.
7. **경계에서 한 번 잡는다 = `InfraStruct.run()`.** 엔진 내부 모듈들은 던지기만 하고,
   중간에서 잡지 않는다. 라이브러리는 `System.exit()` 를 부르지 않는다.

> 판단 주체를 한 줄로: **"다음에 뭘 할지 정할 수 있는 쪽이 처리한다."**
> `CurrentStateStore` 는 "파일 없음 = 최초 실행"은 정할 수 있으니 여기서 끝내고,
> "깨진 파일을 사용자에게 어떻게 알릴지"는 정할 수 없으니 던진다.

#### 스킴 ② 상태로 저장되는 값의 타입 제약 (→ `CONVENTIONS.md` §9)

`CurrentStateStore` 가 JSON 으로 왕복시키므로, 자원이 든 값은 "나갔다 돌아올 수 있는 것"이어야
한다. 프로바이더 작성자에게도 해당된다.

1. **`Kind` 구현체는 enum 이어야 한다.** JSON 에는 문자열밖에 못 남기므로, 복원하려면 값의
   집합이 닫혀 있어야 한다. enum 이 아니면 `StateStoreException`.
2. **`config` 에는 스칼라만.** 자원 참조는 `dependencies` 로 (기존 `ResourceState` 계약).
   섞이면 Comparator 가 "값이 바뀐 것"과 "의존이 바뀐 것"을 구분하지 못한다.
3. **`config` 의 숫자는 정수=`Long`, 소수=`Double` 로 통일한다.** JSON 에 `int`/`long` 구분이
   없으므로 선언 타입은 보존되지 않는다. 파일에서 읽는 쪽은 `CurrentStateStore` 가 맞춘다.
   지금 이 규칙을 **직접 지켜야 하는 곳은 `DesiredStateCreator` 하나**다 — desired 는 파일을
   거치지 않고 바로 Comparator 로 가므로, `int port = 22` 를 그대로 오토박싱하면 `Integer 22` 가
   되어 복원된 `Long 22` 와 어긋난다. 프로바이더의 `Applier` 가 만든 current 는 어차피 파일을
   왕복하며 정규화되므로 필수는 아니지만, 맞춰 주면 헷갈릴 일이 없다.
   → 이 약속 자체를 없애는 방안(`ResourceState` 생성자에서 정규화)은 `docs/plan.md` §9 에 열린 항목.

## 5. 산출물 — 이번에 손대는 파일

| 파일 | 무엇을 |
|---|---|
| `internal/CurrentStateStore.java` | 본문 구현 + 생성자를 `CurrentStateStore(Path)` 로 (§4.2) |
| `internal/StateStoreException.java` | **신규** (§4.4) |
| `spi/Kind.java` | Javadoc 에 "**enum 으로 구현해야 한다**"를 관례가 아닌 요구사항으로 명시 (§4.1) |
| `test/…/CurrentStateStoreTest.java` | **전면 재작성** — 기존 3개는 무인자 생성자와 스텁 계약을 검증하므로 둘 다 사라진다 |
| `.gitignore` | `# infras.state.json` 주석 해제 (이번 구현이 이 파일을 만들기 시작한다) |

기존 테스트를 지우는 것에 대해: 그 3개는 *"스텁이 스텁답게 동작하는가"* 를 본 것이라
(`load()` 가 **항상** 빈 값), 구현이 들어오면 **계약 자체가 바뀐다.** 남겨두면 새 계약과
정면으로 충돌한다.

`.gitignore` 에는 이미 팀이 적어둔 줄이 주석으로 있다 — `# infras.state.json`.
**이번 구현이 그 파일을 실제로 만들어내므로 이번 PR 에서 주석을 푼다.** 상태 파일에는
자원의 실제 설정이 들어가므로 레포에 커밋되면 안 된다. (파일 이름을 `infras.state.json`
으로 정한 근거도 이 줄이다 — §4.2)

**이번 PR 에 들어가지 않는 것**: `CONVENTIONS.md` §8·§9, `CONTRIBUTING.md` §4 요약,
루트 `plan.md` 결정 로그. 전부 별도 브랜치 `docs/exception-and-type-conventions` 에 있다(§4 머리말).
이 브랜치에서 그 파일들을 건드리면 두 PR 이 같은 파일을 고쳐 충돌한다.

## 6. 이번 범위에서 하지 않는 것 (그리고 왜)

- **`ModuleRegistry` 기반 providerId 해석** — 그 타입이 아직 없다 (§4.1 B안).
- **원격 백엔드(S3 등)** — 사용자 요구사항에 "일단 파일" 로 못 박혀 있다.
- **상태 잠금(lock)** — 동시 실행을 막는 장치. 단일 프로세스 실행만 상정한다.
- **스키마 마이그레이션 로직** — `version` 필드는 넣되, 읽을 때 `1` 이 아니면
  예외를 던지는 선까지만. 변환할 옛 버전이 아직 없다.
- **`InfraStruct.run()` 파이프라인 배선** — 그쪽은 여전히 스텁이고 별개 feature 다.
- **에러 표시 정책** (예외를 잡아 사용자용 메시지로 바꾸는 일) — 경계인 `InfraStruct.run()`
  의 몫이다(§4.4). 지금 이 레포에는 `catch` 가 한 곳도 없고, 그 정책이 서는 시점이
  예외 타입을 쪼갤지 판단할 시점이기도 하다.

## 7. 검증 관점 (spec 단계에서 테스트로 바뀔 것들)

이번엔 스텁이 아니라 실제 동작이라 행위 테스트가 제대로 나온다.

**왕복(이번 feature 의 본질)**

- 파일이 없을 때 `load()` 가 빈 `CurrentResources` 를 준다 (기존 계약 유지)
- `save()` 한 뒤 `load()` 하면 같은 내용이 나온다
- 왕복 후 `kind` 가 **원래 enum 상수 그대로**다 (§4.1 A 안이 실제로 서는지)
- `physicalId` 가 `null` 인 자원도 왕복한다
- `config` 의 정수가 `Double` 로 변질되지 않는다 (§4.5)
- 왕복해도 컬렉션이 여전히 불변이다 (`copyOf` 정규화가 살아 있는지 — §2 두 번째 숙제)
- 여러 번 `save()` 하면 마지막 것만 남는다 (덮어쓰기)

**실패 계약 (§4.4) — 조용히 넘어가지 않는지**

- JSON 이 깨진 파일을 `load()` 하면 `StateStoreException` 이 난다 (빈 값 아님)
- `version` 이 모르는 값이면 `StateStoreException` 이 난다
- `kindType` 클래스를 못 찾으면 `StateStoreException` 이 난다
- `Kind` 구현이 enum 이 아니면 `StateStoreException` 이 난다
- 부모 폴더가 없어도 `save()` 가 만들어 준다

테스트는 전부 `@TempDir` 위에서 돈다 (§4.2). `Kind` enum 은 테스트 픽스처로 하나 만든다 —
프로바이더 구현이 이 레포에 없기 때문이다.

> 원자적 쓰기(§4.4)는 "쓰는 도중에 프로세스를 죽인다"를 단위 테스트로 재현하기 어렵다.
> 무리하게 흉내 내는 테스트를 만들기보다, 구현이 `Files.move(ATOMIC_MOVE)` 를 쓰는지로
> 담보하고 이 판단을 summary 에 남긴다. spec 단계에서 다시 조율한다.
