package com.infrastruct.spi;

import java.util.List;
import java.util.Objects;

/**
 * 자원 하나의 변경 전/후 상태와 세부 diff를 담는다.
 *
 * <p>{@code type}이 {@link ChangeType#CREATE}면 {@code before}는 null, {@link ChangeType#DELETE}면
 * {@code after}는 null이다. {@link ChangeType#CREATE}/{@link ChangeType#DELETE}인 경우 {@code
 * fieldDiffs}/{@code dependencyDiffs}는 비어 있다 — 필드 단위 diff는 UPDATE에서만 의미가 있다.
 */
public record ResourceChange(
        ChangeType type,
        CurrentResourceState before,
        DesiredResourceState after,
        List<FieldDiff> fieldDiffs,
        List<DependencyDiff> dependencyDiffs) {

    public ResourceChange {
        Objects.requireNonNull(type, "type");
        fieldDiffs = List.copyOf(fieldDiffs);
        dependencyDiffs = List.copyOf(dependencyDiffs);
    }
}
