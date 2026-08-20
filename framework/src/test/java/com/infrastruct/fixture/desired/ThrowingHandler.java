package com.infrastruct.fixture.desired;

import com.infrastruct.spi.BehaviorHandler;
import com.infrastruct.spi.ScannedResourceState;

/** 처리 중 터지는 핸들러 픽스처. 엔진이 맥락을 붙여 다시 던지는지 보는 장치다. */
public final class ThrowingHandler implements BehaviorHandler<Encrypted> {

    @Override
    public ScannedResourceState handle(Encrypted annotation, ScannedResourceState state) {
        throw new IllegalStateException("handler boom");
    }
}
