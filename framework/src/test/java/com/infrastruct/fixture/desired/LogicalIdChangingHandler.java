package com.infrastruct.fixture.desired;

import com.infrastruct.spi.BehaviorHandler;
import com.infrastruct.spi.ScannedResourceState;

/**
 * 계약을 어기고 {@code logicalId} 를 바꿔 돌려주는 핸들러 픽스처.
 *
 * <p>{@code withConfigEntry} 로는 식별자를 못 바꾸므로 6인자 생성자를 직접 쓴다 — 계약을 어기려면 그 방법밖에 없다는 것 자체가 설계 의도다.
 */
public final class LogicalIdChangingHandler implements BehaviorHandler<Encrypted> {

    @Override
    public ScannedResourceState handle(Encrypted annotation, ScannedResourceState state) {
        return new ScannedResourceState(
                state.kind(),
                state.logicalId() + "-renamed",
                state.config(),
                state.dependencies(),
                state.requiredFields(),
                state.capturedAnnotations());
    }
}
