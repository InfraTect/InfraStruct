package com.infrastruct.fixture.scan.bad.dangling;

import com.infrastruct.api.Resource;
import com.infrastruct.fixture.scan.ScanSubnet;

/**
 * 참조 필드가 {@code @Resource} 없는 클래스를 가리키는 깨진 자원.
 *
 * <p>거부하지 않으면 참조 오타가 조용히 config 로 흘러가고, {@code Class} 객체가 state 파일 직렬화에서 터진다.
 */
@Resource(name = "dangling")
public class DanglingReference extends ScanSubnet {

    {
        vpc = NotAResourceVpc.class;
        cidrBlock = "10.9.0.0/24";
    }
}
