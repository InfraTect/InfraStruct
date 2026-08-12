package com.infrastruct.spi;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;

/**
 * 스캔 직후의 자원 상태. ResourceScanner 가 리플렉션으로 만들어 낸다.
 *
 * <p>{@link ResourceState} 에 더해 자원에 붙어 있던 매크로 어노테이션을 함께 들고 있다. 이것들은 아직 소비되지 않은 "지시서"이고,
 * DesiredStateCreator 가 각 핸들러의 {@link BehaviorHandler#handle} 을 호출해 {@code config} 에 반영한 뒤 {@link
 * DesiredResourceState} 를 만든다.
 */
public class ScannedResourceState extends ResourceState {

    /** 이 자원에서 발견한 매크로 어노테이션들. ({@code @Resource} 는 무조건 붙으므로 담지 않는다.) */
    private List<CapturedAnnotation> capturedAnnotations = new ArrayList<>();

    /** Gson 역직렬화용 인자 없는 생성자. 근거는 {@link ResourceState#ResourceState()}. */
    public ScannedResourceState() {}

    /**
     * 자원의 정체성을 정하는 생성자.
     *
     * @param kind 자원의 종류
     * @param logicalId 자원의 논리 식별자
     */
    public ScannedResourceState(Kind kind, String logicalId) {
        super(kind, logicalId);
    }

    /**
     * 발견한 매크로 어노테이션 목록을 반환한다. 살아 있는 컬렉션이다 — 스캐너가 순차적으로 채운다.
     *
     * @return 가변 {@link CapturedAnnotation} 목록
     */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "스캐너가 발견한 어노테이션을 순차적으로 채우므로 살아 있는 컬렉션을 의도적으로 노출한다")
    public List<CapturedAnnotation> getCapturedAnnotations() {
        return capturedAnnotations;
    }
}
