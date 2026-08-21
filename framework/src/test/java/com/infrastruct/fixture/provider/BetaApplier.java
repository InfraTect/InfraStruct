package com.infrastruct.fixture.provider;

import com.infrastruct.spi.Applier;
import com.infrastruct.spi.CurrentResources;
import com.infrastruct.spi.OrderedResourceChangeSet;

/** 픽스처: {@code beta} 프로바이더의 적용기. alpha 와 <b>다른 타입</b>이어야 한다. */
public class BetaApplier implements Applier {

    @Override
    public CurrentResources apply(OrderedResourceChangeSet plan, CurrentResources current) {
        return current;
    }
}
