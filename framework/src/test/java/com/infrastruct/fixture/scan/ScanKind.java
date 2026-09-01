package com.infrastruct.fixture.scan;

import com.infrastruct.spi.Kind;

/**
 * 스캔 test 전용 {@link Kind} 구현. 실제 프로바이더 레포의 {@code AwsKind} 자리다.
 *
 * <p>framework 는 프로바이더를 의존하지 않으므로 자원 계층을 test 안에서 직접 만든다. 그것이 가능하다는 것 자체가 이 설계의 주장이다.
 */
public enum ScanKind implements Kind {
    Vpc,
    Subnet,
    Ec2,
    Rds;

    @Override
    public String value() {
        return name();
    }
}
