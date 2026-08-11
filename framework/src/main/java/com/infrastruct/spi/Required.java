package com.infrastruct.spi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 자원의 필드에 붙여 그 필드가 필수임을 표시하는 마커.
 *
 * <p>스캐너가 런타임에 읽어 필수 필드 목록을 만든다. 그래서 {@link RetentionPolicy#RUNTIME} 이다.
 *
 * <p>예: {@code @Required public Vpc vpc;}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Required {}
