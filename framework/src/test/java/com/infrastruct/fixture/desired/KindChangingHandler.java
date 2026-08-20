package com.infrastruct.fixture.desired;

import com.infrastruct.spi.BehaviorHandler;
import com.infrastruct.spi.ScannedResourceState;

/** 계약을 어기고 {@code kind} 를 바꿔 돌려주는 핸들러 픽스처. */
public final class KindChangingHandler implements BehaviorHandler<Encrypted> {

    @Override
    public ScannedResourceState handle(Encrypted annotation, ScannedResourceState state) {
        return new ScannedResourceState(
                DesiredKind.SECURITY_GROUP,
                state.logicalId(),
                state.config(),
                state.dependencies(),
                state.requiredFields(),
                state.capturedAnnotations());
    }
}
