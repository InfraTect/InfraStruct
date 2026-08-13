package com.infrastruct.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@link CurrentResources} 는 마지막으로 적용된 상태 목록을 불변으로 감싸는 컨테이너다. */
class CurrentResourcesTest {

    private static final Kind TEST_KIND = () -> "test-kind";

    private static CurrentResourceState state(String logicalId) {
        return new CurrentResourceState(
                TEST_KIND, logicalId, Map.of(), List.of(), Set.of(), "i-0abc123");
    }

    @Test
    void returnsTheResourcesItWasBuiltWith() {
        CurrentResourceState resource = state("ec2.myEc2");

        CurrentResources resources = new CurrentResources(List.of(resource));

        assertThat(resources.resources()).containsExactly(resource);
    }

    @Test
    void laterChangesToTheSourceListDoNotLeakIn() {
        List<CurrentResourceState> source = new ArrayList<>(List.of(state("ec2.myEc2")));
        CurrentResources resources = new CurrentResources(source);

        source.add(state("vpc.myVpc"));

        assertThat(resources.resources()).hasSize(1);
    }

    @Test
    void nullListIsRejected() {
        assertThatNullPointerException().isThrownBy(() -> new CurrentResources(null));
    }
}
