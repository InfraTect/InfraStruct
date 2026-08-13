package com.infrastruct.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@link ScannedResources} 는 스캔 결과 목록을 불변으로 감싸는 컨테이너다. */
class ScannedResourcesTest {

    private static final Kind TEST_KIND = () -> "test-kind";

    private static ScannedResourceState state(String logicalId) {
        return new ScannedResourceState(
                TEST_KIND, logicalId, Map.of(), List.of(), Set.of(), List.of());
    }

    @Test
    void returnsTheResourcesItWasBuiltWith() {
        ScannedResourceState resource = state("ec2.myEc2");

        ScannedResources resources = new ScannedResources(List.of(resource));

        assertThat(resources.resources()).containsExactly(resource);
    }

    @Test
    void laterChangesToTheSourceListDoNotLeakIn() {
        List<ScannedResourceState> source = new ArrayList<>(List.of(state("ec2.myEc2")));
        ScannedResources resources = new ScannedResources(source);

        source.add(state("vpc.myVpc"));

        assertThat(resources.resources()).hasSize(1);
    }

    @Test
    void nullListIsRejected() {
        assertThatNullPointerException().isThrownBy(() -> new ScannedResources(null));
    }
}
