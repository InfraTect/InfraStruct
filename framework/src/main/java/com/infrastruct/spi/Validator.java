package com.infrastruct.spi;

/**
 * 사용자가 원하는 자원 상태의 유효성을 검증하는 공통 기반 클래스.
 *
 * <p>공통 검증은 프레임워크가 제공하고, 프로바이더별 검증은 하위 클래스가 구현한다.
 *
 * <p><b>현재 상태: 스켈레톤이다.</b> 검증 결과 타입과 실제 검증 로직은 아직 결정되지 않아 각 메서드는 임시로 {@code null} 을 반환한다.
 */
public abstract class Validator {

    /**
     * 공통 검증과 프로바이더별 검증을 수행한다.
     *
     * @param desiredResources 검증할 최종 자원 상태 전체
     * @return 검증 결과 (현재는 임시로 {@code null})
     */
    public final Object validate(DesiredResources desiredResources) {
        return null;
    }

    /**
     * 모든 프로바이더에 적용되는 공통 규칙을 검증한다.
     *
     * @param desiredResources 검증할 최종 자원 상태 전체
     * @return 검증 결과 (현재는 임시로 {@code null})
     */
    protected final Object validateCommon(DesiredResources desiredResources) {
        return null;
    }

    /**
     * 프로바이더 고유 규칙을 검증한다.
     *
     * @param desiredResources 검증할 최종 자원 상태 전체
     * @return 검증 결과 (현재는 임시로 {@code null})
     */
    protected Object validateProviderResource(DesiredResources desiredResources) {
        return null;
    }
}
