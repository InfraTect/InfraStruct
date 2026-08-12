package com.infrastruct.spi;

import java.util.Objects;

/**
 * {@code config} 안의 필드 하나가 어떻게 바뀌었는지를 나타낸다.
 *
 * <p>{@code type}이 {@link ChangeType#CREATE}면 {@code oldValue}는 null, {@link ChangeType#DELETE}면
 * {@code newValue}는 null이다.
 */
public record FieldDiff(String field, Object oldValue, Object newValue, ChangeType type) {

    public FieldDiff {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(type, "type");
    }
}
