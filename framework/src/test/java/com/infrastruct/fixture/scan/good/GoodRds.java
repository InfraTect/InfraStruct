package com.infrastruct.fixture.scan.good;

import com.infrastruct.api.Resource;
import com.infrastruct.fixture.scan.ScanRds;
import java.util.List;

/** 정상 자원. 컬렉션 참조가 원소별로 풀리는지 보는 장치다. */
@Resource(name = "deltaRds")
public class GoodRds extends ScanRds {

    {
        subnets = List.of(GoodSubnet.class, OtherSubnet.class);
    }
}
