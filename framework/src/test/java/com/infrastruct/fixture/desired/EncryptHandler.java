package com.infrastruct.fixture.desired;

import com.infrastruct.spi.BehaviorHandler;
import com.infrastruct.spi.ScannedResourceState;

/** {@link Encrypted} 를 config 에 반영하는 핸들러 픽스처. */
public final class EncryptHandler implements BehaviorHandler<Encrypted> {

    @Override
    public ScannedResourceState handle(Encrypted annotation, ScannedResourceState state) {
        return state.withConfigEntry("encrypted", Boolean.TRUE);
    }
}
