package com.infrastruct.fixture.scan.good;

import com.infrastruct.api.Resource;
import com.infrastruct.fixture.scan.ScanSubnet;

/** 정상 자원. {@code az} 를 비워 두어 null 필드가 config 에 안 들어가는지 보는 장치다. */
@Resource(name = "betaSubnet")
public class GoodSubnet extends ScanSubnet {

    {
        vpc = GoodVpc.class;
        cidrBlock = "10.0.1.0/24";
    }
}
