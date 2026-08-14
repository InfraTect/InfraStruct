# spec: current-state-store

`plan.md` 를 테스트 가능한 **행동 목록**으로 옮긴 것. 아래 `- ` 불릿들이 첫 `/red`
때 체크리스트(`behaviors[]`)로 등록되고, 위에서부터 순서대로 red→green 을 돈다.

`CurrentStateStore` 는 **뼈대만** 만드는 feature 다(근거: `plan.md` §1, §4). 따라서 행동은
"공개 시그니처가 존재하고, 스텁 계약(빈 값 반환/무동작)을 지킨다" 수준으로 좁다.

## 행동 목록 (red 사이클 순서)

- CurrentStateStore 는 인자 없이 인스턴스를 만들 수 있다
- load() 는 null 이 아니라 빈 CurrentResources 를 돌려준다
- save(CurrentResources) 는 빈 스텁이라 호출해도 예외 없이 반환한다

## 공개 인터페이스 시그니처 (확정)

### `com.infrastruct.internal.CurrentStateStore`

```java
public final class CurrentStateStore {

    public CurrentResources load() {
        // 본문 비움(스텁). 원래: 상태 파일 읽기 → Gson 역직렬화(Kind TypeAdapter + null 정규화).
        // 파일이 없으면(최초 실행) 빈 CurrentResources 를 돌려준다.
        return new CurrentResources(List.of());
    }

    public void save(CurrentResources resources) {
        // 본문 비움(스텁). 원래: CurrentResources 직렬화 → 상태 파일에 기록.
    }
}
```

## 검증 메모 (어떻게 테스트할지)

> 주의: 이 절은 **행동이 아니라 구현 힌트**다. 하네스가 `- ` 불릿을 행동으로 자동
> 등록하므로, 여기서는 일부러 `- ` 대신 번호 목록을 쓴다.

1. 인스턴스화: `new CurrentStateStore()` 가 예외 없이 만들어진다.
2. load 계약: `store.load()` 가 `null` 이 아니고, `resources()` 가 빈 리스트다
   (최초 실행 = 상태 파일 없음의 자리표시). save→load 라운드트립은 §4 구현 이후 feature 의 몫.
3. save 무동작: `store.save(new CurrentResources(List.of()))` 가 예외 없이 반환한다.
