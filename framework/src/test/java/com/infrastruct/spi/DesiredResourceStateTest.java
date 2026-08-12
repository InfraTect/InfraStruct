package com.infrastruct.spi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link DesiredResourceState} 는 필드를 더하지 않고 {@link ResourceState} 의 계약을 그대로 만족한다. */
class DesiredResourceStateTest {

    /** 픽스처: 프로바이더의 자원 종류. */
    enum TestKind implements Kind {
        VPC;

        @Override
        public String value() {
            return name();
        }
    }

    @Test
    void satisfiesResourceStateContract() {
        DesiredResourceState state = new DesiredResourceState(TestKind.VPC, "myVpc");

        state.getConfig().put("cidrBlock", "10.0.0.0/16");

        assertThat(state.getKind()).isEqualTo(TestKind.VPC);
        assertThat(state.getLogicalId()).isEqualTo("myVpc");
        assertThat(state.getConfig()).containsEntry("cidrBlock", "10.0.0.0/16");
    }
}
