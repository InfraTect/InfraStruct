package com.infrastruct.spi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 프로바이더 토큰 클래스에 붙여, 그 프로바이더의 모듈(스캐너·적용기)을 프레임워크에 등록한다.
 *
 * <p>{@code ModuleRegistry} 가 런타임에 리플렉션으로 읽어 provider 별 모듈을 찾으므로 {@link RetentionPolicy#RUNTIME}
 * 이다.
 *
 * <p>예:
 *
 * <pre>{@code
 * @RegisterProvider(
 *     providerId = "aws",
 *     resourceScanner = AwsResourceScanner.class,
 *     applier = AwsApplier.class)
 * public class Aws extends Provider {}
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RegisterProvider {

    /**
     * 프로바이더 식별자. {@code @InfraStructApplication(provider=...)} 의 값과 매칭된다.
     *
     * @return 프로바이더 식별 문자열 (예: {@code "aws"})
     */
    String providerId();

    /**
     * 이 프로바이더의 자원 스캐너 구현 클래스.
     *
     * <p>상한이 {@code Class<?>} 인 이유: 대상 타입 {@code ResourceScanner} 가 아직 다른 브랜치에서 구현 중이라 참조할 수 없다.
     * 생기면 {@code Class<? extends ResourceScanner>} 로 좁힌다.
     *
     * @return 자원 스캐너 클래스
     */
    Class<?> resourceScanner();

    /**
     * 이 프로바이더의 적용기(applier) 구현 클래스.
     *
     * <p>상한이 {@code Class<?>} 인 이유: 대상 타입 {@code Applier} 가 아직 없다(다른 브랜치). 생기면 {@code Class<?
     * extends Applier>} 로 좁힌다.
     *
     * @return 적용기 클래스
     */
    Class<?> applier();
}
