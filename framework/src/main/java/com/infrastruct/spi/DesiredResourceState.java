package com.infrastruct.spi;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 사용자가 최종적으로 원하는 자원의 상태. Validator 의 검증 대상이고, Comparator 가 "되어야 할 모습"으로 삼는 쪽이다.
 *
 * <p>{@link ScannedResourceState} 의 매크로 어노테이션을 전부 소비해 {@code config} 에 반영한 결과라, 더 이상 풀어야 할 지시서가 남아
 * 있지 않다. 그래서 {@link ResourceState} 에 필드를 더하지 않는다.
 *
 * <p><b>필드가 없는데도 별도 타입인 이유</b>: 파이프라인 시그니처가 이 타입을 이름으로 요구한다({@code
 * Validator.validate(DesiredResources)}). "아직 어노테이션이 안 풀린 날것"과 "검증을 통과할 최종 형태"를 타입으로 갈라 두면 순서를 잘못
 * 밟은 코드가 컴파일 단계에서 걸린다.
 */
public final class DesiredResourceState extends ResourceState {

    /**
     * 사용자가 원하는 최종 상태를 담는 생성자.
     *
     * <p>컬렉션 인자의 {@code null} 처리는 {@link ResourceState#ResourceState(Kind, String, Map, List, Set)}
     * 과 같다.
     *
     * @param kind 자원의 종류
     * @param logicalId 자원의 논리 식별자
     * @param config 어노테이션까지 모두 반영된 스칼라 설정값 모음
     * @param dependencies 의존하는 자원들의 logicalId 목록
     * @param requiredFields {@code @Required} 가 붙은 필드 이름 집합
     * @throws NullPointerException 컬렉션 인자가 {@code null} 이거나 원소에 {@code null} 이 섞인 경우
     */
    public DesiredResourceState(
            Kind kind,
            String logicalId,
            Map<String, Object> config,
            List<String> dependencies,
            Set<String> requiredFields) {
        super(kind, logicalId, config, dependencies, requiredFields);
    }
}
