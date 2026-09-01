package com.infrastruct.fixture.provider;

import com.infrastruct.spi.Applier;
import com.infrastruct.spi.Provider;
import com.infrastruct.spi.RegisterProvider;

/** 픽스처: 구현체 대신 {@link Applier} 인터페이스 그 자체를 등록한 토큰({@code interface-applier}). */
@RegisterProvider(
        providerId = "interface-applier",
        validator = AlphaValidator.class,
        applier = Applier.class)
public class InterfaceApplierProvider extends Provider {}
