package com.infrastruct.fixture.provider;

import com.infrastruct.spi.Provider;
import com.infrastruct.spi.RegisterProvider;

/** 픽스처: 두 번째 정상 토큰. alpha 와 함께 classpath 에 있어 "id 로 고른다"를 검증 가능하게 만든다. */
@RegisterProvider(providerId = "beta", validator = BetaValidator.class, applier = BetaApplier.class)
public class BetaProvider extends Provider {}
