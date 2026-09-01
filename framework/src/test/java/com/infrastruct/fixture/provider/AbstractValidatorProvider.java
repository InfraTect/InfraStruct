package com.infrastruct.fixture.provider;

import com.infrastruct.spi.Provider;
import com.infrastruct.spi.RegisterProvider;

/** 픽스처: validator 자리만 깨뜨린 토큰({@code abstract-validator}). applier 는 정상인 것을 재사용한다. */
@RegisterProvider(
        providerId = "abstract-validator",
        validator = AbstractFixtureValidator.class,
        applier = AlphaApplier.class)
public class AbstractValidatorProvider extends Provider {}
