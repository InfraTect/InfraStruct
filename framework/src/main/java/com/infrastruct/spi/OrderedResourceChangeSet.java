package com.infrastruct.spi;

import java.util.List;

/** {@code PlanCreator}가 {@link ResourceChangeSet}을 의존성 순서에 맞게 정렬한 결과. Applier가 이 순서대로 적용한다. */
public record OrderedResourceChangeSet(List<ResourceChange> diffs) {

    public OrderedResourceChangeSet {
        diffs = List.copyOf(diffs);
    }
}
