package com.infrastruct.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@link DesiredResources} 는 원하는 상태 목록을 불변으로 감싸는 컨테이너다. */
class DesiredResourcesTest {

    private static final Kind TEST_KIND = () -> "test-kind";

    private static DesiredResourceState state(String logicalId) {
        return new DesiredResourceState(TEST_KIND, logicalId, Map.of(), List.of(), Set.of());
    }

    @Test
    void returnsTheResourcesItWasBuiltWith() {
        DesiredResourceState resource = state("ec2.myEc2");

        DesiredResources resources = new DesiredResources(List.of(resource));

        assertThat(resources.resources()).containsExactly(resource);
    }

    @Test
    void laterChangesToTheSourceListDoNotLeakIn() {
        List<DesiredResourceState> source = new ArrayList<>(List.of(state("ec2.myEc2")));
        DesiredResources resources = new DesiredResources(source);

        source.add(state("vpc.myVpc"));

        assertThat(resources.resources()).hasSize(1);
    }

    @Test
    void nullListIsRejected() {
        assertThatNullPointerException().isThrownBy(() -> new DesiredResources(null));
    }
}
