package com.infrastruct.fixture.provider;

import com.infrastruct.spi.Applier;
import com.infrastruct.spi.CurrentResources;
import com.infrastruct.spi.OrderedResourceChangeSet;

/** 픽스처: 인자 있는 생성자만 선언해 무인자 생성자가 없는 적용기. */
public class NoCtorApplier implements Applier {

    private final String region;

    /**
     * 인자를 요구하는 유일한 생성자.
     *
     * @param region 아무 값이나 받는다 — 존재 자체가 요점이다
     */
    public NoCtorApplier(String region) {
        this.region = region;
    }

    @Override
    public CurrentResources apply(OrderedResourceChangeSet plan, CurrentResources current) {
        return current;
    }

    /**
     * 생성자가 받은 값.
     *
     * @return 생성자 인자
     */
    public String region() {
        return region;
    }
}
