package com.infrastruct.spi;

import com.infrastruct.internal.CapturedAnnotation;
import java.util.HashMap;
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
     * {@code config} 에 항목 하나를 더하거나 덮어쓴 <b>새 상태</b>를 돌려준다. 원본은 바뀌지 않는다.
     *
     * <p>불변 객체는 고칠 수 없으므로 고치는 대신 고쳐진 사본을 만든다 — {@code "hello".replace('h', 'j')} 가 원본을 그대로 두고 새
     * 문자열을 주는 것과 같은 관용구다.
     *
     * <p><b>왜 있는가</b>: 이것이 없으면 {@link BehaviorHandler} 구현마다 config 를 복사해 넣고 6인자 생성자로 전 필드를 다시 넘겨야
     * 한다. 핸들러는 {@code capturedAnnotations} 에 관심이 없는데도 손으로 넘겨야 하고, {@code dependencies} 를 빠뜨리면 그 자원의
     * 의존 관계가 <b>조용히</b> 사라진다.
     *
     * @param key 넣을 설정 키
     * @param value 넣을 값 — 스칼라만 (자원 참조는 {@code dependencies} 로 간다)
     * @return 해당 항목만 반영된 새 인스턴스
     */
    public ScannedResourceState withConfigEntry(String key, Object value) {
        Map<String, Object> merged = new HashMap<>(config());
        merged.put(key, value);
        return new ScannedResourceState(
                kind(), logicalId(), merged, dependencies(), requiredFields(), capturedAnnotations);
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
