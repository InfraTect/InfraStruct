package com.infrastruct.fixture.provider;

import com.infrastruct.spi.Provider;
import com.infrastruct.spi.RegisterProvider;

/** 픽스처: validator 의 생성자 접근 제어만 깨뜨린 토큰({@code private-ctor}). */
@RegisterProvider(
        providerId = "private-ctor",
        validator = PrivateCtorValidator.class,
        applier = AlphaApplier.class)
public class PrivateCtorProvider extends Provider {}
