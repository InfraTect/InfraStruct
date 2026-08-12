package com.infrastruct.spi;

import java.util.Objects;

/**
 * {@code dependencies} 목록에서 의존 자원 하나가 추가/제거되었음을 나타낸다. {@link FieldDiff}와 동일한 모양이며, {@code field}에는
 * 대상 의존 자원의 logicalId가 들어간다.
 *
 * <p>{@code type}이 {@link ChangeType#CREATE}면 새로 추가된 의존성({@code oldValue}는 null, {@code newValue}가
 * logicalId), {@link ChangeType#DELETE}면 제거된 의존성({@code oldValue}가 logicalId, {@code newValue}는
 * null)이다.
 */
public record DependencyDiff(String field, Object oldValue, Object newValue, ChangeType type) {

    public DependencyDiff {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(type, "type");
    }
}
