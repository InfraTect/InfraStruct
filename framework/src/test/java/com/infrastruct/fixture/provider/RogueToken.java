package com.infrastruct.fixture.provider;

import com.infrastruct.spi.RegisterProvider;

/**
 * 픽스처: {@code @RegisterProvider} 는 붙었지만 {@code Provider} 를 <b>상속하지 않은</b> 토큰.
 *
 * <p>이 경우가 "미발견"이 아니라 전용 메시지로 실패한다는 것 자체가, 스캔 기준이 상속이 아니라 어노테이션임을 증명한다.
 */
@RegisterProvider(
        providerId = "rogue",
        validator = AlphaValidator.class,
        applier = AlphaApplier.class)
public class RogueToken {}
