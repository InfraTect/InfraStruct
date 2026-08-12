package com.infrastruct.spi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link ResourceState} 는 세 상태 클래스의 공통 부모로 정체성 값과 가변 컬렉션 3종을 제공한다. */
class ResourceStateTest {

    /** 픽스처: 프로바이더의 자원 종류. */
    enum TestKind implements Kind {
        EC2;

        @Override
        public String value() {
            return name();
        }
    }

    /** 픽스처: abstract 라 직접 못 만들므로 최소 하위 클래스를 둔다. */
    static class TestState extends ResourceState {
        TestState(Kind kind, String logicalId) {
            super(kind, logicalId);
        }
    }

    @Test
    void exposesKindAndLogicalIdFromConstructor() {
        TestState state = new TestState(TestKind.EC2, "myEc2");

        assertThat(state.getKind()).isEqualTo(TestKind.EC2);
        assertThat(state.getLogicalId()).isEqualTo("myEc2");
    }

    @Test
    void collectionsStartEmptyAndAreMutable() {
        TestState state = new TestState(TestKind.EC2, "myEc2");

        assertThat(state.getConfig()).isEmpty();
        assertThat(state.getDependencies()).isEmpty();
        assertThat(state.getRequiredFields()).isEmpty();

        state.getConfig().put("instanceType", "t4g.micro");
        state.getDependencies().add("myVpc");
        state.getRequiredFields().add("vpc");

        assertThat(state.getConfig()).containsEntry("instanceType", "t4g.micro");
        assertThat(state.getDependencies()).containsExactly("myVpc");
        assertThat(state.getRequiredFields()).containsExactly("vpc");
    }
}
