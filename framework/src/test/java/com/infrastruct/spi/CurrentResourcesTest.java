package com.infrastruct.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link CurrentResources} 는 마지막으로 적용된 실제 상태 목록을 담는 불변 레코드다. */
class CurrentResourcesTest {

    @Test
    void holdsCurrentResourceStates() {
        CurrentResourceState state = new CurrentResourceState();

        CurrentResources current = new CurrentResources(List.of(state));

        assertThat(current.resources()).containsExactly(state);
    }

    @Test
    void normalizesNullListToEmpty() {
        CurrentResources current = new CurrentResources(null);

        assertThat(current.resources()).isEmpty();
    }

    @Test
    void isNotAffectedByLaterChangesToTheGivenList() {
        List<CurrentResourceState> given = new ArrayList<>();
        given.add(new CurrentResourceState());

        CurrentResources current = new CurrentResources(given);
        given.clear();

        assertThat(current.resources()).hasSize(1);
    }
}
