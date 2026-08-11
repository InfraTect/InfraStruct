package com.infrastruct.spi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link Kind} 는 프로바이더가 enum 으로 구현하는 자원 종류 인터페이스다. */
class KindTest {

    /** 픽스처: 프로바이더가 Kind 를 enum 으로 구현하는 방식을 흉내 낸다. */
    enum TestKind implements Kind {
        EC2;

        @Override
        public String value() {
            return name();
        }
    }

    @Test
    void enumCanImplementKind() {
        Kind kind = TestKind.EC2;

        assertThat(kind.value()).isEqualTo("EC2");
    }
}
