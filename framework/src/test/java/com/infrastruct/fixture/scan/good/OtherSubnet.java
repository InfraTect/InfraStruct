package com.infrastruct.fixture.scan.good;

import com.infrastruct.api.Resource;
import com.infrastruct.fixture.scan.ScanSubnet;

/** 컬렉션 참조의 원소를 2개로 만들기 위한 두 번째 subnet. */
@Resource(name = "epsilonSubnet")
public class OtherSubnet extends ScanSubnet {

    {
        vpc = GoodVpc.class;
        cidrBlock = "10.0.2.0/24";
    }
}
