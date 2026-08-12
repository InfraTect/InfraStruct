package com.infrastruct.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link DesiredResources} 는 원하는 최종 상태 목록을 담는 불변 레코드다. */
class DesiredResourcesTest {

    @Test
    void holdsDesiredResourceStates() {
        DesiredResourceState state = new DesiredResourceState();

        DesiredResources desired = new DesiredResources(List.of(state));

        assertThat(desired.resources()).containsExactly(state);
    }

    @Test
    void normalizesNullListToEmpty() {
        DesiredResources desired = new DesiredResources(null);

        assertThat(desired.resources()).isEmpty();
    }

    @Test
    void isNotAffectedByLaterChangesToTheGivenList() {
        List<DesiredResourceState> given = new ArrayList<>();
        given.add(new DesiredResourceState());

        DesiredResources desired = new DesiredResources(given);
        given.clear();

        assertThat(desired.resources()).hasSize(1);
    }
}
