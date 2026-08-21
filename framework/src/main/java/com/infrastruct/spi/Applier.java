package com.infrastruct.spi;

/**
 * 순서 있는 변경셋을 실제 클라우드에 적용하는 계약. InfraStruct 파이프라인의 마지막 단계다.
 *
 * <p>확장 작성자(프로바이더)가 구현한다. {@code PlanCreator} 가 의존성 순서로 정렬한 {@link OrderedResourceChangeSet} 을 받아
 * 자원을 생성/수정/삭제하고, 적용 후의 실제 상태를 새 {@link CurrentResources} 로 돌려준다.
 */
public interface Applier {

    /**
     * 변경셋을 순서대로 적용하고 적용 후의 실제 상태를 반환한다.
     *
     * <p>{@code current} 에는 직전에 apply 된 실제 상태가 담긴다. {@code plan} 은 바뀐 자원만 담으므로, 구현체는 변경을 적용한 뒤 바뀌지
     * 않은 자원을 {@code current} 에서 이어실어 완전한 {@link CurrentResources} 를 만든다.
     *
     * @param plan 적용할 변경들 (의존성 순서로 정렬됨)
     * @param current 직전에 apply 된 실제 상태
     * @return apply 후의 실제 상태 전체
     */
    CurrentResources apply(OrderedResourceChangeSet plan, CurrentResources current);
}
