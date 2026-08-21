package com.infrastruct.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.infrastruct.spi.DesiredResources;
import com.infrastruct.spi.Kind;
import com.infrastruct.spi.ScannedResourceState;
import com.infrastruct.spi.ScannedResources;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DesiredStateCreatorTest {

    private static final Kind TEST_KIND = () -> "test-kind";

    @Test
    void canBeInstantiatedWithNoArgs() {
        assertThat(new DesiredStateCreator()).isNotNull();
    }

    @Test
    void createReturnsEmptyDesiredResourcesForNowEvenWithScannedInput() {
        ScannedResources scanned =
                new ScannedResources(
                        List.of(
                                new ScannedResourceState(
                                        TEST_KIND,
                                        "vpc.myVpc",
                                        Map.of(),
                                        List.of(),
                                        Set.of(),
                                        List.of())));

        DesiredResources created = new DesiredStateCreator().create(scanned);

        assertThat(created).isNotNull();
        assertThat(created.resources()).isEmpty();
    }
}
