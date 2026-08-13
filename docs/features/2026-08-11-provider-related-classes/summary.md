# summary: provider-related-classes

브랜치: `feat/Provider-Related-Classes-Impl`

## 무엇을 만들었나

| 타입 | 종류 | 패키지 | 파일 |
|---|---|---|---|
| `Kind` | interface | spi | `spi/Kind.java` |
| `Provider` | abstract class | spi | `spi/Provider.java` |
| `ProviderResource` | abstract class | spi | `spi/ProviderResource.java` |
| `@RegisterProvider` | annotation | spi | `spi/RegisterProvider.java` |
| `@Required` | annotation | spi | `spi/Required.java` |
| `@Resource` | annotation | api | `api/Resource.java` |

테스트: `spi/` 에 Kind/Provider/ProviderResource/RegisterProvider/Required 테스트,
`api/` 에 Resource 테스트. 총 6개 행동, 각 1테스트.

## 확정된 계약 (요지)

- `Kind` : `String value()` — 프로바이더가 enum 으로 구현.
- `Provider` : 빈 추상 베이스. `class Aws extends Provider {}`.
- `ProviderResource` : `public Kind kind`, `public Class<? extends Provider> provider`.
- `@Resource(String name)` : RUNTIME/TYPE — 사용자 자원 클래스에.
- `@Required` : RUNTIME/FIELD 마커 — 필수 필드에.
- `@RegisterProvider(String providerId, Class<?> validator, Class<?> applier)` : RUNTIME/TYPE.

## 왜 이렇게 했나 (핵심)

- **패키지**: 프로바이더가 쥐는 것(Kind/Provider/ProviderResource/@RegisterProvider/@Required)은
  spi, 최종 사용자가 쥐는 `@Resource` 는 api.
- **`@RegisterProvider` 의 `validator`/`applier` 는 `Class<?>`**: 대상 타입
  `Validator`/`Applier` 가 아직 없고(다른 브랜치) 연쇄 의존까지 있어, 지금 상한을 걸 수 없다.
  타입이 생기면 `Class<? extends ...>` 로 좁힌다(각 속성에 TODO 주석).
  - (정정) 초기엔 `resourceScanner` 로 잘못 넣었으나, 다이어그램 "새 프로바이더 추가" 노트대로
    `@RegisterProvider` 는 **validator·applier** 를 저장하는 게 맞다 → `validator` 로 수정.
- **`@Required` 는 spi**: 주로 프로바이더가 자원 템플릿 필드에 붙인다.

## SpotBugs 오탐 처리 (국소 예외 — 정책 아님)

`ProviderResource.kind`/`provider` 는 하위 클래스가 채우고 엔진이 리플렉션으로 읽어, 이 모듈
정적 분석엔 "안 쓰이는 public 필드"로 보여 `UUF_UNUSED_PUBLIC_OR_PROTECTED_FIELD` 오탐이 난다.

- 처리: 두 필드에만 `@SuppressFBWarnings` (+ `spotbugs-annotations` compileOnly). 코드 옆에 사유 주석.
- **전역 규칙은 보류**: 데이터 포인트가 이거 하나뿐이라 성급. `ResourceState` 등 상태 클래스에서
  같은 패턴이 반복되면 "라이브러리 공개 필드 정책"으로 재검토.

## 다음(이 브랜치 밖)

- ~~`Validator`/`Applier` 가 생기면 `@RegisterProvider` 의 두 속성 상한을 좁힌다.~~
  → `applier()` 는 ✅ 해결 (`feat/impl-applier-interface`): `Class<? extends Applier>` 로 좁힘.
  `validator()` 는 `Validator` 타입이 아직 없어 여전히 `Class<?>` (대기).
- 실제 프로바이더 구현(Aws, AwsKind, AwsEc2 …)은 프로바이더 레포/브랜치 몫.
