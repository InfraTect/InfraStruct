package com.infrastruct.fixture.desired;

import com.infrastruct.spi.Behavior;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 멤버가 없는 매크로 어노테이션 픽스처. 인자 없이도 동작하는 경로를 위해 둔다. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Behavior(handler = EncryptHandler.class)
public @interface Encrypted {}
