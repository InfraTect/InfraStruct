package com.infrastruct.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link ScannedResources} 는 스캔 결과 목록을 담는 불변 레코드다. */
class ScannedResourcesTest {

    @Test
    void holdsScannedResourceStates() {
        ScannedResourceState state = new ScannedResourceState();

        ScannedResources resources = new ScannedResources(List.of(state));

        assertThat(resources.scannedResources()).containsExactly(state);
    }

    @Test
    void normalizesNullListToEmpty() {
        ScannedResources resources = new ScannedResources(null);

        assertThat(resources.scannedResources()).isEmpty();
    }

    @Test
    void isNotAffectedByLaterChangesToTheGivenList() {
        List<ScannedResourceState> given = new ArrayList<>();
        given.add(new ScannedResourceState());

        ScannedResources resources = new ScannedResources(given);
        given.clear();

        assertThat(resources.scannedResources()).hasSize(1);
    }
}
