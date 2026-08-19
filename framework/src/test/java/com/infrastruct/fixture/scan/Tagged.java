package com.infrastruct.fixture.scan;

import com.infrastruct.spi.Behavior;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 매크로 annotation 포착을 보는 fixture. 실제 프로바이더의 {@code @AllowSSH} 자리다.
 *
 * <p>멤버 {@link #value()} 를 둔 이유는 포착된 것이 <b>실제로 붙어 있던 인스턴스</b>인지 확인하기 위해서다. 멤버가 없으면 새로 만든 빈
 * annotation 을 넣어도 test 가 통과한다.
 */
@Behavior(handler = TagHandler.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Tagged {

    /**
     * 자원에 붙일 태그 값.
     *
     * @return 태그 값
     */
    String value() default "default-tag";
}
