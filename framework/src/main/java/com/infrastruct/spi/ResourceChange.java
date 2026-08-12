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

        switch (type) {
            case CREATE -> {
                if (before != null) {
                    throw new IllegalArgumentException("CREATE는 before가 null이어야 한다");
                }
                Objects.requireNonNull(after, "after");
                requireNoDiffs(fieldDiffs, dependencyDiffs, type);
            }
            case DELETE -> {
                Objects.requireNonNull(before, "before");
                if (after != null) {
                    throw new IllegalArgumentException("DELETE는 after가 null이어야 한다");
                }
                requireNoDiffs(fieldDiffs, dependencyDiffs, type);
            }
            case UPDATE -> {
                Objects.requireNonNull(before, "before");
                Objects.requireNonNull(after, "after");
            }
        }
    }

    private static void requireNoDiffs(
            List<FieldDiff> fieldDiffs, List<DependencyDiff> dependencyDiffs, ChangeType type) {
        if (!fieldDiffs.isEmpty() || !dependencyDiffs.isEmpty()) {
            throw new IllegalArgumentException(type + "는 fieldDiffs/dependencyDiffs가 비어 있어야 한다");
        }
    }
}
