package com.infrastruct.spi;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** <b>임시 스켈레톤</b>: 자원 상태관리 담당(선현진)이 실제 구현으로 교체할 예정. */
public final class CurrentResourceState extends ResourceState {

    private final String physicalId;

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

    public String physicalId() {
        return physicalId;
    }
}
