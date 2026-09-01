package com.infrastruct.fixture.desired;

import com.infrastruct.spi.BehaviorHandler;
import com.infrastruct.spi.ScannedResourceState;

/** 계약(public 무인자 생성자)을 어긴 핸들러 픽스처. 인자 있는 생성자만 있다. */
public final class NoDefaultCtorHandler implements BehaviorHandler<Encrypted> {

    private final String label;

    /**
     * 인자를 받는 유일한 생성자.
     *
     * @param label config 에 넣을 값
     */
    public NoDefaultCtorHandler(String label) {
        this.label = label;
    }

    @Override
    public ScannedResourceState handle(Encrypted annotation, ScannedResourceState state) {
        return state.withConfigEntry("label", label);
    }
}
