package com.infrastruct.spi;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** <b>임시 스켈레톤</b>: 자원 상태관리 담당(선현진)이 실제 구현으로 교체할 예정. */
public final class DesiredResourceState extends ResourceState {

    public DesiredResourceState(
            Kind kind,
            String logicalId,
            Map<String, Object> config,
            List<String> dependencies,
            Set<String> requiredFields) {
        super(kind, logicalId, config, dependencies, requiredFields);
    }
}
