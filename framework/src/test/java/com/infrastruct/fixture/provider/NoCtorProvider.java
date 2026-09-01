package com.infrastruct.fixture.provider;

import com.infrastruct.spi.Provider;
import com.infrastruct.spi.RegisterProvider;

/** 픽스처: applier 자리만 깨뜨린 토큰({@code no-ctor}). */
@RegisterProvider(
        providerId = "no-ctor",
        validator = AlphaValidator.class,
        applier = NoCtorApplier.class)
public class NoCtorProvider extends Provider {}
