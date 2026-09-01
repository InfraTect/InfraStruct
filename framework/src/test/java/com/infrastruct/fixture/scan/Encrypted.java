package com.infrastruct.fixture.scan;

import com.infrastruct.spi.Behavior;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 매크로 annotation 포착 <b>정렬</b>을 보는 fixture.
 *
 * <p>{@code GoodEc2} 에 {@code @Tagged @Encrypted} 순으로 붙는다. type 이름 사전순 정렬이 실제로 동작하면 포착 결과에서는 이쪽이
 * 앞이어야 한다.
 */
@Behavior(handler = EncryptHandler.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Encrypted {}
