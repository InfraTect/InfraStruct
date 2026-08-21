package com.infrastruct.fixture.provider;

import com.infrastruct.spi.Provider;
import com.infrastruct.spi.RegisterProvider;

/** 픽스처: {@code twin} id 를 {@link TwinOneProvider} 와 중복 선언한 토큰. */
@RegisterProvider(
        providerId = "twin",
        validator = AlphaValidator.class,
        applier = AlphaApplier.class)
public class TwinTwoProvider extends Provider {}
