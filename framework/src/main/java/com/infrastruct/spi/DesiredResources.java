package com.infrastruct.spi;

import java.util.List;

/** <b>임시 스켈레톤</b>: 자원 상태관리 담당(선현진)이 실제 구현으로 교체할 예정. */
public record DesiredResources(List<DesiredResourceState> resources) {

    public DesiredResources {
        resources = List.copyOf(resources);
    }
}
