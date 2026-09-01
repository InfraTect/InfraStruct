package com.infrastruct.fixture.scan;

import com.infrastruct.spi.Required;

/** 스칼라 필드가 config 로 가는지 보는 자원 타입. */
public abstract class ScanVpc extends ScanResource {

    @Required public String cidrBlock;

    protected ScanVpc() {
        this.kind = ScanKind.Vpc;
    }
}
