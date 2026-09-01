package com.infrastruct.fixture.desired;

import com.infrastruct.spi.Behavior;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 인자를 가진 매크로 어노테이션 픽스처.
 *
 * <p>멤버를 둔 이유: 어노테이션 <b>인스턴스</b>가 실제로 핸들러까지 전달되는지 반증 가능하게 만들기 위해서다. 멤버가 없으면 타입만 보고 값을 하드코딩한 구현도
 * 테스트를 통과한다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Behavior(handler = AllowPortHandler.class)
public @interface AllowPort {

    /**
     * 열어 줄 포트.
     *
     * @return 포트 번호
     */
    int port() default 22;
}
