package com.infrastruct.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@link ResourceState} 는 세 상태 타입의 공통 필드를 불변으로 보관하는 부모다. */
class ResourceStateTest {

    private static final Kind TEST_KIND = () -> "test-kind";

    /** 픽스처: abstract 인 {@link ResourceState} 를 세우기 위한 최소 하위 타입. */
    static final class FixtureState extends ResourceState {
        FixtureState(
                Kind kind,
                String logicalId,
                Map<String, Object> config,
                List<String> dependencies,
                Set<String> requiredFields) {
            super(kind, logicalId, config, dependencies, requiredFields);
        }
    }

    private static FixtureState fixture(Map<String, Object> config, List<String> dependencies) {
        return new FixtureState(TEST_KIND, "vpc.myVpc", config, dependencies, Set.of("cidrBlock"));
    }

    @Test
    void accessorsReturnConstructorArguments() {
        FixtureState state = fixture(Map.of("cidrBlock", "10.0.0.0/16"), List.of("vpc.other"));

        assertThat(state.kind()).isSameAs(TEST_KIND);
        assertThat(state.logicalId()).isEqualTo("vpc.myVpc");
        assertThat(state.config()).containsExactly(Map.entry("cidrBlock", "10.0.0.0/16"));
        assertThat(state.dependencies()).containsExactly("vpc.other");
        assertThat(state.requiredFields()).containsExactly("cidrBlock");
    }

    @Test
    void laterChangesToSourceCollectionsDoNotLeakIn() {
        Map<String, Object> config = new HashMap<>(Map.of("cidrBlock", "10.0.0.0/16"));
        List<String> dependencies = new ArrayList<>(List.of("vpc.other"));
        FixtureState state = fixture(config, dependencies);

        config.put("tenancy", "default");
        dependencies.add("subnet.a");

        assertThat(state.config()).hasSize(1);
        assertThat(state.dependencies()).containsExactly("vpc.other");
    }

    @Test
    void returnedCollectionsAreUnmodifiable() {
        FixtureState state = fixture(Map.of("cidrBlock", "10.0.0.0/16"), List.of("vpc.other"));

        assertThatThrownBy(() -> state.config().put("tenancy", "default"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> state.dependencies().add("subnet.a"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> state.requiredFields().add("tenancy"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullCollectionIsRejected() {
        assertThatNullPointerException().isThrownBy(() -> fixture(null, List.of("vpc.other")));
        assertThatNullPointerException()
                .isThrownBy(() -> fixture(Map.of("cidrBlock", "10.0.0.0/16"), null));
    }
}
