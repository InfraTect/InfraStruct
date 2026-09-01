package com.infrastruct.fixture.provider;

import com.infrastruct.spi.Provider;
import com.infrastruct.spi.RegisterProvider;

/** 픽스처: validator 생성자만 터지게 만든 토큰({@code throwing}). */
@RegisterProvider(
        providerId = "throwing",
        validator = ThrowingValidator.class,
        applier = AlphaApplier.class)
public class ThrowingProvider extends Provider {}
