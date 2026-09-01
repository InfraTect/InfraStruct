package com.infrastruct.fixture.scan;

import com.infrastruct.spi.Required;

/**
 * 단일 참조를 보는 자원 타입.
 *
 * <p>참조 필드가 {@code Class<? extends T>} 인 이유는 {@code plan.md} §5 다. 다이어그램의 {@code @Required public
 * Vpc vpc;} 는 그대로는 컴파일되지 않는다.
 *
 * <p>{@code az} 는 값을 안 넣어 <b>null 필드</b>를 만드는 장치다.
 */
public abstract class ScanSubnet extends ScanResource {

    @Required public Class<? extends ScanVpc> vpc;

    @Required public String cidrBlock;

    public String az;

    protected ScanSubnet() {
        this.kind = ScanKind.Subnet;
    }
}
