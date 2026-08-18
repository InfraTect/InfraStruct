package com.infrastruct.fixture.scan.bad.noctor;

import com.infrastruct.api.Resource;
import com.infrastruct.fixture.scan.ScanVpc;

/** 깨진 자원: 인자 있는 생성자만 있어 인스턴스화할 수 없다. */
@Resource(name = "noCtor")
public class NoNoArgConstructor extends ScanVpc {

    /**
     * 인자 없는 생성자를 막기 위한 생성자.
     *
     * @param cidr 쓰이지 않는다
     */
    public NoNoArgConstructor(String cidr) {
        this.cidrBlock = cidr;
    }
}
