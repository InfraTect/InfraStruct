package com.infrastruct.fixture.scan.good;

import com.infrastruct.api.Resource;
import com.infrastruct.fixture.scan.Plain;
import com.infrastruct.fixture.scan.ScanSubnet;
import com.infrastruct.fixture.scan.Tagged;

/**
 * 정상 자원. {@code az} 를 비워 두어 null 필드가 config 에 안 들어가는지 보는 장치다.
 *
 * <p>{@code @Tagged("net")} 은 포착된 annotation 이 실제 인스턴스인지(멤버 값이 살아 있는지), {@code @Plain} 은
 * {@code @Behavior} 없는 annotation 이 포착되지 않는지 본다.
 */
@Resource(name = "betaSubnet")
@Tagged("net")
@Plain
public class GoodSubnet extends ScanSubnet {

    {
        vpc = GoodVpc.class;
        cidrBlock = "10.0.1.0/24";
    }
}
