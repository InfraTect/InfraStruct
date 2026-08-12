package com.infrastruct.spi;

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
public class DesiredResourceState extends ResourceState {

    /** Gson 역직렬화용 인자 없는 생성자. 근거는 {@link ResourceState#ResourceState()}. */
    public DesiredResourceState() {}

    /**
     * 자원의 정체성을 정하는 생성자.
     *
     * @param kind 자원의 종류
     * @param logicalId 자원의 논리 식별자
     */
    public DesiredResourceState(Kind kind, String logicalId) {
        super(kind, logicalId);
    }
}
