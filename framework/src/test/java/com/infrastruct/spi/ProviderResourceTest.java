package com.infrastruct.spi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link ProviderResource} 는 모든 자원의 루트로 kind/provider 를 보유한다. */
class ProviderResourceTest {

    /** 픽스처: 프로바이더 토큰. */
    static class TestProvider extends Provider {}

    /** 픽스처: 프로바이더의 자원 종류. */
    enum TestKind implements Kind {
        EC2;

        @Override
        public String value() {
            return name();
        }
    }

    /** 픽스처: 하위 자원이 kind/provider 를 채우는 방식을 흉내 낸다. */
    static class TestResource extends ProviderResource {
        TestResource() {
            this.kind = TestKind.EC2;
            this.provider = TestProvider.class;
        }
    }

    @Test
    void subclassSetsKindAndProvider() {
        TestResource resource = new TestResource();

        assertThat(resource.kind).isEqualTo(TestKind.EC2);
        assertThat(resource.provider).isEqualTo(TestProvider.class);
    }
}
