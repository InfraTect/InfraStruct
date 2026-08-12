package com.infrastruct.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@link DesiredResourceState} 는 필드를 더하지 않고 {@link ResourceState} 의 계약을 그대로 만족한다. */
class DesiredResourceStateTest {

    private static final Kind TEST_KIND = () -> "test-kind";

    @Test
    void satisfiesParentContractWithoutExtraFields() {
        DesiredResourceState state =
                new DesiredResourceState(
                        TEST_KIND,
                        "vpc.myVpc",
                        Map.of("cidrBlock", "10.0.0.0/16"),
                        List.of("igw.main"),
                        Set.of("cidrBlock"));

        assertThat(state).isInstanceOf(ResourceState.class);
        assertThat(state.kind()).isSameAs(TEST_KIND);
        assertThat(state.logicalId()).isEqualTo("vpc.myVpc");
        assertThat(state.config()).containsExactly(Map.entry("cidrBlock", "10.0.0.0/16"));
        assertThat(state.dependencies()).containsExactly("igw.main");
        assertThat(state.requiredFields()).containsExactly("cidrBlock");
    }
}
