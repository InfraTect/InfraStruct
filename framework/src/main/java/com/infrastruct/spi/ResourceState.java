package com.infrastruct.spi;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 자원 상태 클래스들의 공통 부모.
 *
 * <p><b>임시 스켈레톤</b>: 자원 상태관리 담당(선현진)이 실제 구현으로 교체할 예정. Comparator/ ResourceChange 구현·테스트가 돌아가려면 최소한의
 * 필드 구조가 필요해서 우선 맞춰 둔 것.
 */
public abstract class ResourceState {

    private final Kind kind;
    private final String logicalId;
    private final Map<String, Object> config;
    private final List<String> dependencies;
    private final Set<String> requiredFields;

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

    public Kind kind() {
        return kind;
    }

    public String logicalId() {
        return logicalId;
    }

    public Map<String, Object> config() {
        return config;
    }

    public List<String> dependencies() {
        return dependencies;
    }

    public Set<String> requiredFields() {
        return requiredFields;
    }
}
