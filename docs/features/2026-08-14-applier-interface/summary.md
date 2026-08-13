# summary: applier-interface

브랜치: `feat/impl-applier-interface`

## 무엇을 만들었나

| 타입 | 종류 | 패키지 | 파일 |
|---|---|---|---|
| `Applier` | **신규** interface | spi | `spi/Applier.java` |
| `@RegisterProvider` | `applier()` 상한 좁힘 | spi | `spi/RegisterProvider.java` |

테스트: `spi/ApplierTest`(신규), `spi/RegisterProviderTest`(픽스처·상한 검증 추가).
행동 2개, 각 red→green.

## 확정된 계약

```java
// 파이프라인 마지막 단계 — 프로바이더가 구현한다.
public interface Applier {
    CurrentResources apply(OrderedResourceChangeSet plan, CurrentResources current);
}

// @RegisterProvider: applier() 상한이 좁혀졌다.
Class<? extends Applier> applier();   // (전) Class<?> + TODO
```

## 왜 이렇게 했나 (핵심)

- **패키지 = spi**: 프로바이더 작성자가 `implements Applier` 하는 확장점. (엔진 소유인
  `Comparator`(internal)와 대조 — `resource-state-classes/plan.md` §5 의 판별을 따름.)
- **`current` 도 받는다**: `plan`(`OrderedResourceChangeSet`)에는 바뀐 자원만 들어 있고
  (`ChangeType` 은 CREATE/UPDATE/DELETE 뿐), 안 바뀐 자원을 새 `CurrentResources` 에
  이어실으려면 직전 상태가 필요하다. 그래서 `apply(plan, current)` 로 둘 다 받아 완전한 상태를
  반환한다.
- **반환형 = `CurrentResources`**: `CurrentResources` Javadoc 의 예약("Applier 가 적용 후
  새로 만들어 반환한다")을 그대로 실현. physicalId 는 apply 시점에 채워진다.
- **`applier()` 상한을 이번에 함께 좁혔다**: `Applier` 가 생겼으니 `RegisterProvider` 의
  자리표시자를 `Class<? extends Applier>` 로 조였다. 상한이 실제로 좁혀졌음은 어노테이션
  메서드의 **제네릭 반환 타입**을 리플렉션으로 읽어 와일드카드 상한이 `Applier` 인지 확인하는
  테스트로 못박았다 (값 왕복만으론 `Class<?>` 로도 통과해 RED 가 안 나기 때문).

## 범위 밖 (그대로 둔 것)

- 실제 프로바이더의 `Applier` 구현체(AWS 등) — 사용례.
- `Validator` 인터페이스, 그리고 `RegisterProvider.validator()` 상한 — `Validator` 타입이
  아직 없어 손대지 않았다.
- `InfraStruct` 파이프라인의 `apply` 호출부 배선 — `ModuleRegistry` 가 아직 없다.
- **apply 실패/부분 적용/롤백 시맨틱**: 초기 계약에서 다루지 않고 열어 뒀다. 실제 호출부와
  프로바이더가 생길 때 예외 정책으로 확장한다.

## 다음

- `Validator` 인터페이스가 생기면 `RegisterProvider.validator()` 상한도 같은 방식으로 좁힌다
  (이번엔 `applier()` 만 처리).
- `Applier` 를 실제로 부르는 곳(`InfraStruct.apply` 파이프라인)은 `ModuleRegistry`·`PlanCreator`
  가 준비된 뒤 배선하며, 그때 apply 실패 시맨틱을 확정한다.
