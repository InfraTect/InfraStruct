package com.infrastruct.spi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link CurrentResourceState} 는 부모 계약에 더해 클라우드가 발급한 physicalId 를 들고 있다. */
class CurrentResourceStateTest {

    /** 픽스처: 프로바이더의 자원 종류. */
    enum TestKind implements Kind {
        EC2;

        @Override
        public String value() {
            return name();
        }
    }

    @Test
    void exposesPhysicalIdFromConstructor() {
        CurrentResourceState state = new CurrentResourceState(TestKind.EC2, "myEc2", "i-0abc123");

        assertThat(state.getKind()).isEqualTo(TestKind.EC2);
        assertThat(state.getLogicalId()).isEqualTo("myEc2");
        assertThat(state.getPhysicalId()).isEqualTo("i-0abc123");
    }

    @Test
    void physicalIdIsAssignableAfterApply() {
        CurrentResourceState state = new CurrentResourceState();

        assertThat(state.getPhysicalId()).isNull();
        state.setPhysicalId("i-0abc123");

        assertThat(state.getPhysicalId()).isEqualTo("i-0abc123");
    }
}
