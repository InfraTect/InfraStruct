package com.infrastruct.spi;

import com.infrastruct.internal.CapturedAnnotation;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 스캔 직후의 자원 상태. ResourceScanner 가 리플렉션으로 만들어 낸다.
 *
 * <p>{@link ResourceState} 에 더해 자원에 붙어 있던 매크로 어노테이션을 함께 들고 있다. 이것들은 아직 소비되지 않은 "지시서"이고,
 * DesiredStateCreator 가 각 핸들러의 {@link BehaviorHandler#handle} 을 호출해 {@code config} 에 반영한 뒤 {@link
 * DesiredResourceState} 를 만든다.
 */
public final class ScannedResourceState extends ResourceState {

    private final List<CapturedAnnotation> capturedAnnotations;

    /**
     * 스캔 결과를 담는 생성자.
     *
     * <p>컬렉션 인자의 {@code null} 처리는 {@link ResourceState#ResourceState(Kind, String, Map, List, Set)}
     * 과 같다.
     *
     * @param kind 자원의 종류
     * @param logicalId 자원의 논리 식별자
     * @param config 스칼라 설정값 모음
     * @param dependencies 의존하는 자원들의 logicalId 목록
     * @param requiredFields {@code @Required} 가 붙은 필드 이름 집합
     * @param capturedAnnotations 이 자원에서 발견한 매크로 어노테이션들 ({@code @Resource} 는 제외)
     * @throws NullPointerException 컬렉션 인자가 {@code null} 이거나 원소에 {@code null} 이 섞인 경우
     */
    public ScannedResourceState(
            Kind kind,
            String logicalId,
            Map<String, Object> config,
            List<String> dependencies,
            Set<String> requiredFields,
            List<CapturedAnnotation> capturedAnnotations) {
        super(kind, logicalId, config, dependencies, requiredFields);
        this.capturedAnnotations = List.copyOf(capturedAnnotations);
    }

    /**
     * 발견한 매크로 어노테이션 목록을 반환한다.
     *
     * <p>{@code @Resource} 는 모든 자원에 무조건 붙으므로 담지 않는다 — 여기 있는 것은 전부 핸들러로 풀어야 할 지시서다.
     *
     * @return 불변 {@code List<CapturedAnnotation>}
     */
    public List<CapturedAnnotation> capturedAnnotations() {
        return capturedAnnotations;
    }
}
