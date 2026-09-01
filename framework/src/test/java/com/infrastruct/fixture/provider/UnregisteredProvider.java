package com.infrastruct.fixture.provider;

import com.infrastruct.spi.Provider;

/**
 * 픽스처: {@code Provider} 를 상속했지만 {@code @RegisterProvider} 가 <b>없는</b> 토큰.
 *
 * <p>스캔 기준이 상속이 아니라 어노테이션임을 반증 가능하게 만든다 — 발견된 id 목록에 이 클래스가 섞이면 안 된다.
 */
public class UnregisteredProvider extends Provider {}
