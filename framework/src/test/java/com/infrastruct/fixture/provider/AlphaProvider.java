package com.infrastruct.fixture.provider;

import com.infrastruct.spi.Provider;
import com.infrastruct.spi.RegisterProvider;

/** 픽스처: 정상 등록된 {@code alpha} 프로바이더 토큰. 실제 프로바이더의 {@code Aws} 자리를 흉내 낸다. */
@RegisterProvider(
        providerId = "alpha",
        validator = AlphaValidator.class,
        applier = AlphaApplier.class)
public class AlphaProvider extends Provider {}
