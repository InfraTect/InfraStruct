package com.infrastruct.fixture.scan.good;

import com.infrastruct.api.Resource;
import com.infrastruct.fixture.scan.Encrypted;
import com.infrastruct.fixture.scan.ScanEc2;
import com.infrastruct.fixture.scan.Tagged;
import java.util.Map;

/**
 * 정상 자원. {@code owner} 로 조부모 {@code ScanResource.owner} 를 shadowing 해 자식 값이 이기는지 본다.
 *
 * <p>annotation 을 {@code @Tagged @Encrypted} 순으로 붙인 것은 <b>정렬이 실제로 뒤집는지</b> 보려는 것이다. type 이름 사전순이면
 * {@code Encrypted} 가 앞이어야 한다.
 */
@Resource(name = "gammaEc2")
@Tagged
@Encrypted public class GoodEc2 extends ScanEc2 {

    /** 부모의 {@code infra-team} 을 가린다. */
    public String owner = "team-b";

    {
        subnet = GoodSubnet.class;
        vpc = GoodVpc.class;
        tags = Map.of("team", "infra");
    }
}
