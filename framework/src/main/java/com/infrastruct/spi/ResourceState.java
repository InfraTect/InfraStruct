package com.infrastruct.spi;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
 * <p>직접 인스턴스화할 자리가 없어 {@code abstract} 다.
 */
public abstract class ResourceState {

    /** 자원의 종류. 프로바이더 {@link Kind} 구현체(enum)가 들어간다. */
    private Kind kind;

    /** 자원의 논리 식별자. {@code @Resource(name)} 값이다. */
    private String logicalId;

    /**
     * 자원의 설정값 모음. <b>스칼라 값만 담는다</b> — 자원 참조는 {@link #getDependencies()} 로 간다.
     *
     * <p>순서가 안정적인 {@link LinkedHashMap} 을 쓰는 이유: 상태 파일(JSON) 의 diff 가 실행마다 흔들리지 않아야 한다.
     */
    private Map<String, Object> config = new LinkedHashMap<>();

    /** 이 자원이 의존하는 다른 자원들의 logicalId 목록. */
    private List<String> dependencies = new ArrayList<>();

    /**
     * {@code @Required} 가 붙은 필드의 이름들.
     *
     * <p>{@link ScannedResourceState} 와 {@link DesiredResourceState} 만 쓴다. {@link
     * CurrentResourceState} 는 이미 적용된 실제 상태라 "필수 여부"라는 개념이 없어 비워 둔다.
     */
    private Set<String> requiredFields = new LinkedHashSet<>();

    /**
     * 인자 없는 생성자.
     *
     * <p>Gson 역직렬화용이다. 이게 없으면 Gson 이 {@code Unsafe} 로 인스턴스를 만들어 위 컬렉션 초기화를 건너뛰고, {@code config} 가
     * {@code null} 인 객체가 나와 뒤 단계가 터진다.
     */
    protected ResourceState() {}

    /**
     * 자원의 정체성을 정하는 생성자.
     *
     * @param kind 자원의 종류
     * @param logicalId 자원의 논리 식별자
     */
    protected ResourceState(Kind kind, String logicalId) {
        this.kind = kind;
        this.logicalId = logicalId;
    }

    /**
     * 자원의 종류를 반환한다.
     *
     * @return 프로바이더의 {@link Kind} 구현체
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * 자원의 논리 식별자를 반환한다.
     *
     * @return {@code @Resource(name)} 값
     */
    public String getLogicalId() {
        return logicalId;
    }

    /**
     * 설정값 맵을 반환한다.
     *
     * <p><b>살아 있는 컬렉션이다 — 방어 복사를 하지 않는다.</b> {@link BehaviorHandler#handle} 이 {@code void} 라 핸들러가
     * 넘겨받은 상태를 직접 고쳐야 하기 때문이다. 여기에 넣은 값은 그대로 이 상태에 반영된다.
     *
     * @return 가변 설정값 맵
     */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "핸들러가 상태를 직접 수정해야 하므로 살아 있는 컬렉션을 의도적으로 노출한다")
    public Map<String, Object> getConfig() {
        return config;
    }

    /**
     * 의존 자원의 logicalId 목록을 반환한다. {@link #getConfig()} 와 같은 이유로 살아 있는 컬렉션이다.
     *
     * @return 가변 의존성 목록
     */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "스캐너가 발견한 의존성을 순차적으로 채우므로 살아 있는 컬렉션을 의도적으로 노출한다")
    public List<String> getDependencies() {
        return dependencies;
    }

    /**
     * 필수 필드 이름 집합을 반환한다. {@link #getConfig()} 와 같은 이유로 살아 있는 컬렉션이다.
     *
     * @return 가변 필수 필드 이름 집합
     */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "스캐너가 발견한 필수 필드를 순차적으로 채우므로 살아 있는 컬렉션을 의도적으로 노출한다")
    public Set<String> getRequiredFields() {
        return requiredFields;
    }
}
