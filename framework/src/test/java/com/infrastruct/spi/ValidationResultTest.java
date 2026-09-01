package com.infrastruct.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link ValidationResult}가 위반 목록을 불변으로 보관하고 병합하는지 검증한다. */
class ValidationResultTest {

    private static final Violation FIRST =
            new Violation("FIRST", "resource.one", "firstField", "first violation");
    private static final Violation SECOND =
            new Violation("SECOND", "resource.one", "secondField", "second violation");

    @Test
    void validResultHasEmptyUnmodifiableViolations() {
        ValidationResult result = ValidationResult.valid();

        assertThat(result.isValid()).isTrue();
        assertThat(result.violations()).isEmpty();
        assertThatThrownBy(() -> result.violations().add(FIRST))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void copiesAndPreservesMultipleViolationsForOneResource() {
        List<Violation> source = new ArrayList<>(List.of(FIRST, SECOND));
        ValidationResult result = new ValidationResult(source);

        source.clear();

        assertThat(result.isValid()).isFalse();
        assertThat(result.violations()).containsExactly(FIRST, SECOND);
    }

    @Test
    void mergeCombinesBothResultsWithoutChangingEitherInput() {
        ValidationResult first = new ValidationResult(List.of(FIRST));
        ValidationResult second = new ValidationResult(List.of(SECOND));

        ValidationResult merged = first.merge(second);

        assertThat(merged.violations()).containsExactly(FIRST, SECOND);
        assertThat(first.violations()).containsExactly(FIRST);
        assertThat(second.violations()).containsExactly(SECOND);
    }
}
