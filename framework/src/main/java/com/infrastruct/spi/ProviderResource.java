package com.infrastruct.spi;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * 모든 자원의 루트 클래스. 프로바이더가 정의하는 자원 계층(예: AwsResource → AwsEc2)의 최상위다.
 *
 * <p>하위 클래스가 {@link #kind} 와 {@link #provider} 를 채운다. 엔진은 이 값들을 리플렉션으로 읽어 자원의 종류와 소속 프로바이더를 파악한다.
 */
public abstract class ProviderResource {

    // SpotBugs 억제 사유(아래 두 필드):
    //   이 필드들은 (1) 하위 자원 클래스(별도 프로바이더 레포)가 값을 채우고,
    //   (2) 엔진 스캐너가 리플렉션으로 읽는다. 둘 다 이 모듈의 정적 분석엔 안 보여
    //   SpotBugs 가 "안 쓰이는 public 필드(UUF)"로 오탐한다. 라이브러리 공개 필드라 정상.
    //   ※ 국소 예외로만 둔다. 상태 클래스들에서 같은 패턴이 반복되면 프로젝트 정책으로 재검토.

    /** 자원의 종류. 프로바이더 Kind 구현체(enum)가 들어간다. */
    @SuppressFBWarnings(
            value = "UUF_UNUSED_PUBLIC_OR_PROTECTED_FIELD",
            justification = "하위 클래스가 채우고 엔진이 리플렉션으로 읽는다 — 이 모듈에선 미사용처럼 보이는 오탐")
    public Kind kind;

    /** 이 자원이 속한 프로바이더 토큰 타입. 예: {@code Aws.class}. */
    @SuppressFBWarnings(
            value = "UUF_UNUSED_PUBLIC_OR_PROTECTED_FIELD",
            justification = "하위 클래스가 채우고 엔진이 리플렉션으로 읽는다 — 이 모듈에선 미사용처럼 보이는 오탐")
    public Class<? extends Provider> provider;
}
