package com.infrastruct.spi;

/**
 * 자원의 종류를 나타내는 계약. 프로바이더는 자신의 자원 목록을 <b>반드시 enum 으로 구현해야 한다</b> — 관례가 아니라 요구사항이다.
 *
 * <p>예: {@code enum AwsKind implements Kind { EC2, VPC, ...; }}.
 *
 * <p>enum 이어야 하는 이유 두 가지:
 *
 * <ol>
 *   <li>상태 파일에서 복원하려면 값의 집합이 닫혀 있어야 한다. {@code CurrentStateStore} 는 저장된 {@code kindType} 을 enum 상수
 *       목록에서 {@link #value()} 로 되찾는다.
 *   <li>enum 이 아니면 {@code CurrentStateStore} 가 {@code StateStoreException} 을 던져 저장 자체가 실패한다.
 * </ol>
 */
public interface Kind {

    /**
     * 자원 종류의 문자열 식별자.
     *
     * @return 자원 종류 이름 (예: {@code "EC2"})
     */
    String value();
}
