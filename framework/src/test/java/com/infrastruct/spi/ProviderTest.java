package com.infrastruct.spi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link Provider} 는 프로바이더 토큰 클래스가 상속하는 베이스다. */
class ProviderTest {

    /** 픽스처: {@code class Aws extends Provider {}} 를 흉내 낸다. */
    static class TestProvider extends Provider {}

    @Test
    void canBeExtended() {
        Provider provider = new TestProvider();

        assertThat(provider).isInstanceOf(Provider.class);
    }
}
