package com.infrastruct.fixture.scan.good;

import com.infrastruct.api.Resource;
import com.infrastruct.fixture.scan.ScanEc2;

/** 정상 자원. {@code owner} 로 조부모 {@code ScanResource.owner} 를 shadowing 해 자식 값이 이기는지 본다. */
@Resource(name = "gammaEc2")
public class GoodEc2 extends ScanEc2 {

    /** 부모의 {@code infra-team} 을 가린다. */
    public String owner = "team-b";

    {
        subnet = GoodSubnet.class;
    }
}
