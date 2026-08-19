package com.infrastruct.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infrastruct.spi.ScannedResources;
import org.junit.jupiter.api.Test;

class ResourceScannerTest {

    private static final String GOOD = "com.infrastruct.fixture.scan.good";

    @Test
    void canBeInstantiatedWithNoArgs() {
        assertThat(new ResourceScanner()).isNotNull();
    }

    @Test
    void canBeInstantiatedWithBasePackage() {
        assertThat(new ResourceScanner(GOOD)).isNotNull();
    }

    @Test
    void keepsBasePackageGivenToConstructor() {
        assertThat(new ResourceScanner(GOOD).basePackage()).isEqualTo(GOOD);
    }

    @Test
    void treatsNoArgConstructorAsWholeClasspath() {
        assertThat(new ResourceScanner().basePackage()).isNull();
    }

    @Test
    void scanReturnsEmptyScannedResourcesForNow() {
        ScannedResources scanned = new ResourceScanner(GOOD).scan();

        assertThat(scanned).isNotNull();
        assertThat(scanned.resources()).isEmpty();
    }

    @Test
    void resourceScanExceptionCarriesMessage() {
        assertThatThrownBy(
                        () -> {
                            throw new ResourceScanException("com.example.MyEc2 의 name 이 비었다");
                        })
                .isInstanceOf(RuntimeException.class)
                .hasMessage("com.example.MyEc2 의 name 이 비었다");
    }

    @Test
    void resourceScanExceptionKeepsCause() {
        NoSuchMethodException cause = new NoSuchMethodException("<init>");

        assertThatThrownBy(
                        () -> {
                            throw new ResourceScanException("com.example.MyEc2 인스턴스화 실패", cause);
                        })
                .isInstanceOf(ResourceScanException.class)
                .hasCause(cause);
    }
}
