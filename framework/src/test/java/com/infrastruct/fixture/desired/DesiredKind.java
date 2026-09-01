package com.infrastruct.fixture.desired;

import com.infrastruct.spi.Kind;

/**
 * DesiredStateCreator 테스트용 자원 종류.
 *
 * <p>{@link Kind} 구현이 enum 이어야 하는 이유는 CONVENTIONS §9.1 — 상태 파일(JSON)에서 복원하려면 값의 집합이 닫혀 있어야 한다.
 */
public enum DesiredKind implements Kind {
    VPC,
    SECURITY_GROUP;

    @Override
    public String value() {
        return name();
    }
}
