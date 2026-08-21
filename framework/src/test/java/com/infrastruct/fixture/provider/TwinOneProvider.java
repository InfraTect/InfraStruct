package com.infrastruct.fixture.provider;

import com.infrastruct.spi.Provider;
import com.infrastruct.spi.RegisterProvider;

/** 픽스처: {@code twin} id 를 {@link TwinTwoProvider} 와 중복 선언한 토큰. 구현체 자리는 정상인 alpha 것을 재사용한다. */
@RegisterProvider(
        providerId = "twin",
        validator = AlphaValidator.class,
        applier = AlphaApplier.class)
public class TwinOneProvider extends Provider {}
