package com.infrastruct.spi;

/**
 * 자원의 종류를 나타내는 계약. 프로바이더가 자신의 자원 목록을 enum 으로 구현한다.
 *
 * <p>예: {@code enum AwsKind implements Kind { EC2, VPC, ...; }}.
 */
public interface Kind {

    /**
     * 자원 종류의 문자열 식별자.
     *
     * @return 자원 종류 이름 (예: {@code "EC2"})
     */
    String value();
}
