package com.infrastruct.fixture.desired;

import com.infrastruct.spi.BehaviorHandler;
import com.infrastruct.spi.ScannedResourceState;

/** {@link AllowPort} 의 인자 값을 config 에 넣는 핸들러 픽스처. */
public final class AllowPortHandler implements BehaviorHandler<AllowPort> {

    @Override
    public ScannedResourceState handle(AllowPort annotation, ScannedResourceState state) {
        return state.withConfigEntry("port", annotation.port());
    }
}
