package com.infrastruct.fixture.scan;

import com.infrastruct.spi.Required;

/** 기본값이 있는 스칼라 필드를 보는 자원 타입. */
public abstract class ScanEc2 extends ScanResource {

    @Required public Class<? extends ScanSubnet> subnet;

    public String instanceType = "t3.micro";

    protected ScanEc2() {
        this.kind = ScanKind.Ec2;
    }
}
