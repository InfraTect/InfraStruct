package com.infrastruct.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 공통 검증과 프로바이더 검증에서 발견한 위반 사항 전체. */
public record ValidationResult(List<Violation> violations) {

    /**
     * 위반 목록을 불변으로 복사한다.
     *
     * @throws NullPointerException {@code violations}가 {@code null}이거나 원소에 {@code null}이 섞인 경우
     */
    public ValidationResult {
        violations = List.copyOf(violations);
    }

    /**
     * 위반이 없는 정상 결과를 만든다.
     *
     * @return 빈 위반 목록을 가진 결과
     */
    public static ValidationResult valid() {
        return new ValidationResult(List.of());
    }

    /**
     * 검증 통과 여부를 반환한다.
     *
     * @return 위반이 하나도 없으면 {@code true}
     */
    public boolean isValid() {
        return violations.isEmpty();
    }

    /**
     * 이 결과와 다른 결과의 위반 목록을 순서대로 합친다.
     *
     * @param other 뒤에 합칠 검증 결과
     * @return 두 결과의 위반을 모두 가진 새 결과
     * @throws NullPointerException {@code other}가 {@code null}인 경우
     */
    public ValidationResult merge(ValidationResult other) {
        Objects.requireNonNull(other, "other");
        List<Violation> merged = new ArrayList<>(violations);
        merged.addAll(other.violations());
        return new ValidationResult(merged);
    }
}
