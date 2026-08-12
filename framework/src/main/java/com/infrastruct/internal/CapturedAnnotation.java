package com.infrastruct.internal;

import com.infrastruct.spi.BehaviorHandler;
import java.lang.annotation.Annotation;

/**
 * 스캔 때 자원에서 발견한 "매크로 어노테이션 + 그것을 처리할 핸들러 클래스" 쌍.
 *
 * <p>스캐너가 만들어 ScannedResourceState 에 담고, DesiredStateCreator 가 나중에 {@code handlerClass} 의 {@link
 * BehaviorHandler#handle} 을 호출해 소비한다. ({@code @Resource} 는 무조건 붙으므로 여기 담지 않는다.)
 *
 * <p>불변 데이터 묶음이라 record 로 둔다.
 *
 * @param anno 발견한 어노테이션 인스턴스
 * @param handlerClass 그 어노테이션을 처리할 핸들러 클래스
 */
public record CapturedAnnotation(
        Annotation anno, Class<? extends BehaviorHandler<?>> handlerClass) {}
