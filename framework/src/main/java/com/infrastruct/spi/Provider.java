package com.infrastruct.spi;

/**
 * 프로바이더를 식별하는 토큰 클래스의 부모.
 *
 * <p>프로바이더는 이 클래스를 상속해 자신을 나타내는 빈 토큰을 만든다. 예: {@code class Aws extends Provider {}}. 여기에 {@link
 * RegisterProvider} 를 붙여 모듈을 등록한다.
 *
 * <p>직접 인스턴스화할 이유가 없어 {@code abstract} 다.
 */
public abstract class Provider {}
