package com.infrastruct.spi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 매크로 어노테이션 선언에 붙여, 그 어노테이션을 처리할 핸들러를 지정한다.
 *
 * <p>스캐너가 런타임에 읽어 핸들러를 찾으므로 {@link RetentionPolicy#RUNTIME} 이고, 다른 어노테이션 선언에 붙으므로 {@link
 * ElementType#ANNOTATION_TYPE} 이다.
 *
 * <p>예: {@code @Behavior(AllowSshHandler.class) public @interface AllowSSH {}}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface Behavior {

    /**
     * 이 매크로 어노테이션을 처리할 핸들러 클래스.
     *
     * @return {@link BehaviorHandler} 구현 클래스
     */
    Class<? extends BehaviorHandler<?>> handler();
}
