package com.infrastruct.fixture.desired;

import com.infrastruct.spi.BehaviorHandler;
import com.infrastruct.spi.ScannedResourceState;

/**
 * 자기 인스턴스의 신원을 config 에 찍는 핸들러 픽스처. 인스턴스 재사용 검증용.
 *
 * <p>static 카운터를 쓰지 않는 이유: 테스트 간 전역 상태를 만들면 실행 순서에 따라 깨진다. 자원 둘에 같은 값이 찍혔다는 것만으로 "같은 인스턴스"는 충분히
 * 증명된다.
 */
public final class IdentityStampingHandler implements BehaviorHandler<Encrypted> {

    @Override
    public ScannedResourceState handle(Encrypted annotation, ScannedResourceState state) {
        return state.withConfigEntry("handlerId", System.identityHashCode(this));
    }
}
