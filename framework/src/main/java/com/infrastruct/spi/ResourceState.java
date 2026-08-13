package com.infrastruct.spi;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 프레임워크가 내부적으로 관리하는 자원 상태의 공통 부모.
 *
 * <p>파이프라인의 단계마다 같은 자원이 다른 얼굴로 나타난다 — 스캔 직후({@link ScannedResourceState}), 사용자가 원하는 최종 모습({@link
 * DesiredResourceState}), 마지막으로 적용된 실제 모습({@link CurrentResourceState}). 셋이 공통으로 갖는 것을 여기 모았다.
 *
 * <p><b>{@code config} 와 {@code dependencies} 의 경계가 이 클래스의 핵심이다.</b> 스칼라 설정값은 {@code config} 로, 다른
 * 자원을 가리키는 참조는 {@code dependencies} 로 간다. 섞으면 Comparator 가 "값이 바뀐 것"과 "의존 관계가 바뀐 것"을 구분하지 못한다.
 *
 * <p><b>불변이다.</b> 파이프라인의 각 단계는 앞 단계의 상태를 고치지 않고 새 상태를 만들어 다음 단계로 넘긴다. 덕분에 Comparator 가 비교하는 도중에 대상이
 * 바뀔 여지가 없고, 상태 하나를 여러 단계가 동시에 들고 있어도 안전하다. 값을 바꾸려면 고친 값으로 새 인스턴스를 만든다.
 *
 * <p>직접 인스턴스화할 자리가 없어 {@code abstract} 다.
 */
public abstract class ResourceState {

    private final Kind kind;
    private final String logicalId;
    private final Map<String, Object> config;
    private final List<String> dependencies;
    private final Set<String> requiredFields;

    /**
     * 하위 타입이 공통 필드를 채우는 생성자.
     *
     * <p>세 컬렉션은 모두 불변으로 복사해 보관한다 — 호출자가 넘긴 컬렉션을 나중에 고쳐도 이 상태는 영향을 받지 않는다. 그래서 {@code null} 을 넘기면
     * {@link NullPointerException} 이 난다. 값이 없으면 {@code Map.of()} / {@code List.of()} / {@code
     * Set.of()} 를 넘긴다.
     *
     * @param kind 자원의 종류
     * @param logicalId 자원의 논리 식별자 ({@code @Resource(name)} 값)
     * @param config 스칼라 설정값 모음 — 자원 참조는 넣지 않는다
     * @param dependencies 의존하는 자원들의 logicalId 목록
     * @param requiredFields {@code @Required} 가 붙은 필드 이름 집합
     * @throws NullPointerException 인자 중 컬렉션이 {@code null} 이거나 원소에 {@code null} 이 섞인 경우
     */
    protected ResourceState(
            Kind kind,
            String logicalId,
            Map<String, Object> config,
            List<String> dependencies,
            Set<String> requiredFields) {
        this.kind = kind;
        this.logicalId = logicalId;
        this.config = Map.copyOf(config);
        this.dependencies = List.copyOf(dependencies);
        this.requiredFields = Set.copyOf(requiredFields);
    }

    /**
     * 자원의 종류를 반환한다.
     *
     * @return 프로바이더의 {@link Kind} 구현체 (보통 enum 상수)
     */
    public Kind kind() {
        return kind;
    }

    /**
     * 자원의 논리 식별자를 반환한다.
     *
     * <p>Comparator 가 current 와 desired 를 짝지을 때 쓰는 키라, 한 번 정해지면 파이프라인 내내 바뀌지 않는다.
     *
     * @return {@code @Resource(name)} 값
     */
    public String logicalId() {
        return logicalId;
    }

    /**
     * 설정값 맵을 반환한다.
     *
     * <p><b>스칼라 값만 들어 있다</b> — 자원 참조는 {@link #dependencies()} 로 간다.
     *
     * @return 불변 {@code Map<String, Object>}
     */
    public Map<String, Object> config() {
        return config;
    }

    /**
     * 의존 자원의 logicalId 목록을 반환한다.
     *
     * @return 불변 {@code List<String>}
     */
    public List<String> dependencies() {
        return dependencies;
    }

    /**
     * {@code @Required} 가 붙은 필드의 이름 집합을 반환한다.
     *
     * <p>{@link ScannedResourceState} 와 {@link DesiredResourceState} 만 채운다. {@link
     * CurrentResourceState} 는 이미 적용된 실제 상태라 "필수 여부"라는 개념이 없어 항상 비어 있다.
     *
     * @return 불변 {@code Set<String>}
     */
    public Set<String> requiredFields() {
        return requiredFields;
    }
}
