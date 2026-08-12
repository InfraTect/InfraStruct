package com.infrastruct.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 사용자가 정의한 클래스가 클라우드 자원임을 표시하고 그 논리 식별자(logicalId)를 정한다.
 *
 * <p>이 어노테이션이 붙은 클래스만 스캔 대상이 된다. 런타임에 리플렉션으로 읽으므로 {@link RetentionPolicy#RUNTIME} 이다.
 *
 * <p>예: {@code @Resource(name = "myEc2") public class MyEc2 extends AwsEc2 {}}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Resource {

    /**
     * 자원의 논리 식별자(logicalId).
     *
     * @return 자원 이름
     */
    String name();
}
