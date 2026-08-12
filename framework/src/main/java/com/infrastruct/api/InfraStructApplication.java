package com.infrastruct.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 애플리케이션의 진입(메인) 클래스에 붙여, 이 애플리케이션이 사용할 프로바이더를 선언한다.
 *
 * <p>런타임에 리플렉션으로 읽어 프로바이더를 결정하므로 {@link RetentionPolicy#RUNTIME} 이다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface InfraStructApplication {

    /**
     * 사용할 프로바이더 식별자. 예: {@code "aws"}.
     *
     * <p>문자열인 이유: 코어가 특정 클라우드 목록을 알면 안 되기 때문. 런타임에 이 값으로 프로바이더를 탐색한다.
     *
     * @return 프로바이더 식별 문자열
     */
    String provider();
}
