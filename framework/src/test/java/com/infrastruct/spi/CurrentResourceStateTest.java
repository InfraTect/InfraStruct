package com.infrastruct.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@link CurrentResourceState} 는 공통 필드에 클라우드가 발급한 physicalId 를 더해 들고 있다. */
class CurrentResourceStateTest {

    private static final Kind TEST_KIND = () -> "test-kind";

    private static CurrentResourceState fixture(String physicalId) {
        return new CurrentResourceState(
                TEST_KIND,
                "ec2.myEc2",
                Map.of("instanceType", "t3.micro"),
                List.of("vpc.myVpc"),
                Set.of(),
                physicalId);
    }

    @Test
    void holdsPhysicalIdOnTopOfCommonFields() {
        CurrentResourceState state = fixture("i-0abc123");

        assertThat(state.logicalId()).isEqualTo("ec2.myEc2");
        assertThat(state.physicalId()).isEqualTo("i-0abc123");
    }

    @Test
    void physicalIdIsNullBeforeApply() {
        CurrentResourceState state = fixture(null);

        assertThat(state.physicalId()).isNull();
    }

    @Test
    void requiredFieldsStayEmptyForAnAlreadyAppliedResource() {
        CurrentResourceState state = fixture("i-0abc123");

        assertThat(state.requiredFields()).isEmpty();
    }
}
