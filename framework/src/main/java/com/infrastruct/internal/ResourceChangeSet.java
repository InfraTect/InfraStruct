package com.infrastruct.internal;

import com.infrastruct.spi.ResourceChange;
import java.util.List;

/** {@link Comparator}가 만드는, 의존성 순서를 아직 고려하지 않은 변경 목록. */
public record ResourceChangeSet(List<ResourceChange> diffs) {

    public ResourceChangeSet {
        diffs = List.copyOf(diffs);
    }
}
