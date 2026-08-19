package com.infrastruct.fixture.scan;

import com.infrastruct.spi.Required;
import java.util.Map;

/**
 * 기본값이 있는 스칼라 필드와 <b>참조 필드 2개</b>를 보는 자원 타입.
 *
 * <p>참조 필드가 둘({@code subnet}, {@code vpc})인 이유는 dependencies 의 순서가 필드명 사전순으로 고정되는지 보기 위해서다. 하나뿐이면
 * 정렬하지 않는 구현도 통과한다.
 *
 * <p>{@code tags} 는 참조가 아닌 {@code Map} 값이 config 로 가는지 보는 장치다.
 */
public abstract class ScanEc2 extends ScanResource {

    @Required public Class<? extends ScanSubnet> subnet;

    @Required public Class<? extends ScanVpc> vpc;

    public Map<String, String> tags;

    public String instanceType = "t3.micro";

    protected ScanEc2() {
        this.kind = ScanKind.Ec2;
    }
}
