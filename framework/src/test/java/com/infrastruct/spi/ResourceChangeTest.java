package com.infrastruct.spi;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@link ResourceChange} 는 type별 before/after/diff 계약을 생성 시점에 강제한다. */
class ResourceChangeTest {

    private static final Kind TEST_KIND = () -> "test-kind";

    private static final CurrentResourceState BEFORE =
            new CurrentResourceState(
                    TEST_KIND, "vpc.myVpc", Map.of(), List.of(), Set.of(), "vpc-1");
    private static final DesiredResourceState AFTER =
            new DesiredResourceState(TEST_KIND, "vpc.myVpc", Map.of(), List.of(), Set.of());

    @Test
    void validCreateSucceeds() {
        assertThatNoException()
                .isThrownBy(
                        () ->
                                new ResourceChange(
                                        ChangeType.CREATE, null, AFTER, List.of(), List.of()));
    }

    @Test
    void createWithNonNullBeforeThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new ResourceChange(
                                        ChangeType.CREATE, BEFORE, AFTER, List.of(), List.of()));
    }

    @Test
    void createWithNullAfterThrows() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new ResourceChange(
                                        ChangeType.CREATE, null, null, List.of(), List.of()));
    }

    @Test
    void createWithFieldDiffsThrows() {
        FieldDiff fieldDiff = new FieldDiff("cidrBlock", null, "10.0.0.0/16", ChangeType.CREATE);

        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new ResourceChange(
                                        ChangeType.CREATE,
                                        null,
                                        AFTER,
                                        List.of(fieldDiff),
                                        List.of()));
    }

    @Test
    void validDeleteSucceeds() {
        assertThatNoException()
                .isThrownBy(
                        () ->
                                new ResourceChange(
                                        ChangeType.DELETE, BEFORE, null, List.of(), List.of()));
    }

    @Test
    void deleteWithNullBeforeThrows() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new ResourceChange(
                                        ChangeType.DELETE, null, null, List.of(), List.of()));
    }

    @Test
    void deleteWithNonNullAfterThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new ResourceChange(
                                        ChangeType.DELETE, BEFORE, AFTER, List.of(), List.of()));
    }

    @Test
    void deleteWithDependencyDiffsThrows() {
        DependencyDiff dependencyDiff =
                new DependencyDiff("vpc.oldVpc", "vpc.oldVpc", null, ChangeType.DELETE);

        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new ResourceChange(
                                        ChangeType.DELETE,
                                        BEFORE,
                                        null,
                                        List.of(),
                                        List.of(dependencyDiff)));
    }

    @Test
    void validUpdateSucceeds() {
        assertThatNoException()
                .isThrownBy(
                        () ->
                                new ResourceChange(
                                        ChangeType.UPDATE, BEFORE, AFTER, List.of(), List.of()));
    }

    @Test
    void updateWithNullBeforeThrows() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new ResourceChange(
                                        ChangeType.UPDATE, null, AFTER, List.of(), List.of()));
    }

    @Test
    void updateWithNullAfterThrows() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new ResourceChange(
                                        ChangeType.UPDATE, BEFORE, null, List.of(), List.of()));
    }
}
