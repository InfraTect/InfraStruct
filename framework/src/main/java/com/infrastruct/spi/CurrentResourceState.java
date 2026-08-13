package com.infrastruct.spi;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 마지막으로 apply 된 자원의 실제 상태. CurrentStateStore 가 JSON 으로 저장하고 다시 읽어 들이는 대상이다.
 *
 * <p>{@link DesiredResourceState} 가 "되어야 할 모습"이라면 이쪽은 "지금 실제 모습"이다. Comparator 가 둘을 비교해 변경 목록을 만든다.
 *
 * <p>{@code requiredFields} 는 채우지 않는다 — 이미 적용된 상태에는 "필수 여부"라는 개념이 없다. 근거는 {@link
 * ResourceState#requiredFields()} 참조.
 */
public final class CurrentResourceState extends ResourceState {

    private final String physicalId;

    /**
     * 적용 결과를 담는 생성자.
     *
     * <p>컬렉션 인자의 {@code null} 처리는 {@link ResourceState#ResourceState(Kind, String, Map, List, Set)}
     * 과 같다. {@code physicalId} 만은 {@code null} 을 허용한다 — 아래 파라미터 설명 참조.
     *
     * @param kind 자원의 종류
     * @param logicalId 자원의 논리 식별자
     * @param config 실제로 적용된 스칼라 설정값 모음
     * @param dependencies 의존하는 자원들의 logicalId 목록
     * @param requiredFields 이 타입에서는 쓰지 않는다 — {@code Set.of()} 를 넘긴다
     * @param physicalId 클라우드가 발급한 식별자. Applier 가 자원을 만들기 전에는 알 수 없으므로 {@code null} 일 수 있다
     * @throws NullPointerException 컬렉션 인자가 {@code null} 이거나 원소에 {@code null} 이 섞인 경우
     */
    public CurrentResourceState(
            Kind kind,
            String logicalId,
            Map<String, Object> config,
            List<String> dependencies,
            Set<String> requiredFields,
            String physicalId) {
        super(kind, logicalId, config, dependencies, requiredFields);
        this.physicalId = physicalId;
    }

    /**
     * 클라우드가 실제로 발급한 식별자를 반환한다. 예: {@code i-0abc123}.
     *
     * <p>logicalId 가 사용자가 붙인 이름이라면 이쪽은 클라우드가 붙인 이름이다. Applier 가 실제 자원을 지우거나 고칠 때 쓰는 값이다.
     *
     * @return 아직 apply 되지 않았으면 {@code null}
     */
    public String physicalId() {
        return physicalId;
    }
}
