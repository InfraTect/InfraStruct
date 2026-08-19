package com.infrastruct.fixture.scan;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code @Behavior} 가 <b>없는</b> annotation. 포착되면 안 되는 반증 장치다.
 *
 * <p>이것이 없으면 "매크로 annotation 만 포착한다"가 아니라 "전부 포착한다"로 짠 구현도 test 를 통과해 버린다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Plain {}
