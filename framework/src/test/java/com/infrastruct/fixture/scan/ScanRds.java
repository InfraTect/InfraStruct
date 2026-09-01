package com.infrastruct.fixture.scan;

import com.infrastruct.spi.Required;
import java.util.List;

/**
 * 컬렉션 참조를 보는 자원 타입. RDS 가 서로 다른 AZ 의 subnet 2개 이상을 요구하는 것을 본뜬다 ({@code plan.md} §5).
 *
 * <p>이 fixture 가 없으면 단일 참조만 처리하는 구현이 그냥 통과한다.
 */
public abstract class ScanRds extends ScanResource {

    @Required public List<Class<? extends ScanSubnet>> subnets;

    public String engine = "mysql";

    protected ScanRds() {
        this.kind = ScanKind.Rds;
    }
}
