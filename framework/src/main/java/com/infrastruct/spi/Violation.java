package com.infrastruct.spi;

import java.util.Objects;

/** 자원 검증에서 발견한 위반 사항 하나. */
public record Violation(String code, String logicalId, String field, String message) {

    /**
     * 위반 종류와 사용자용 메시지는 반드시 존재해야 한다.
     *
     * <p>{@code logicalId}와 {@code field}는 오류 위치를 특정할 수 없을 때 {@code null}일 수 있다.
     *
     * @throws NullPointerException {@code code} 또는 {@code message}가 {@code null}인 경우
     */
    public Violation {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
