package com.infrastruct.spi;

import java.lang.annotation.Annotation;

/**
 * 매크로 어노테이션 하나의 효과를 자원 상태에 반영하는 핸들러 계약.
 *
 * <p>확장 작성자가 매크로 어노테이션(예: {@code @AllowSSH})마다 이 인터페이스를 구현한다. 스캔 단계에서 수집된 어노테이션을
 * DesiredStateCreator 가 해당 핸들러의 {@link #handle} 로 전달해 config 에 반영한다.
 *
 * @param <T> 이 핸들러가 처리하는 어노테이션 타입
 */
public interface BehaviorHandler<T extends Annotation> {

    /**
     * 어노테이션의 효과를 자원 상태에 반영한다.
     *
     * <p>{@code state} 의 타입이 {@link Object} 인 이유: 원래는 {@code ScannedResourceState} 이지만 그 타입이 아직 다른
     * 영역에서 구현 중이라 참조할 수 없다. 타입이 생기면 {@code ScannedResourceState} 로 좁힌다.
     *
     * @param annotation 처리할 어노테이션 인스턴스
     * @param state 반영 대상 자원 상태 (임시로 {@code Object})
     */
    void handle(T annotation, Object state);
}
