package com.infrastruct.fixture.provider;

import com.infrastruct.spi.Applier;
import com.infrastruct.spi.CurrentResources;
import com.infrastruct.spi.OrderedResourceChangeSet;

/** 픽스처: {@code alpha} 프로바이더의 정상 적용기. 받은 상태를 그대로 돌려준다. */
public class AlphaApplier implements Applier {

    @Override
    public CurrentResources apply(OrderedResourceChangeSet plan, CurrentResources current) {
        return current;
    }
}
