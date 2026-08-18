package com.infrastruct.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infrastruct.api.Resource;
import com.infrastruct.fixture.scan.ScanKind;
import com.infrastruct.fixture.scan.good.GoodEc2;
import com.infrastruct.fixture.scan.good.GoodRds;
import com.infrastruct.fixture.scan.good.GoodSubnet;
import com.infrastruct.fixture.scan.good.GoodVpc;
import com.infrastruct.fixture.scan.good.OtherSubnet;
import com.infrastruct.spi.ScannedResourceState;
import com.infrastruct.spi.ScannedResources;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ResourceScannerTest {

    private static final String GOOD = "com.infrastruct.fixture.scan.good";
    private static final String BAD = "com.infrastruct.fixture.scan.bad";

    // ── 시그니처 ─────────────────────────────────────────────────

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

    // ── 발견 ─────────────────────────────────────────────────────

    @Test
    void findsEveryAnnotatedResourceUnderBasePackage() {
        ScannedResources scanned = new ResourceScanner(GOOD).scan();

        assertThat(scanned.resources()).hasSize(5);
        assertThat(scanned.resources())
                .extracting(ScannedResourceState::logicalId)
                .containsExactlyInAnyOrder(
                        "alphaVpc", "betaSubnet", "gammaEc2", "deltaRds", "epsilonSubnet");
    }

    @Test
    void ignoresResourcesOutsideBasePackage() {
        assertThatCode(() -> new ResourceScanner(GOOD).scan()).doesNotThrowAnyException();

        assertThat(new ResourceScanner(GOOD).scan().resources())
                .extracting(ScannedResourceState::logicalId)
                .doesNotContain("twin", "noKind", "notProvider");
    }

    @Test
    void ordersResourcesByClassName() {
        List<String> expected =
                Stream.of(
                                GoodEc2.class,
                                GoodRds.class,
                                GoodSubnet.class,
                                GoodVpc.class,
                                OtherSubnet.class)
                        .sorted(java.util.Comparator.comparing(Class::getName))
                        .map(type -> type.getAnnotation(Resource.class).name())
                        .toList();

        assertThat(new ResourceScanner(GOOD).scan().resources())
                .extracting(ScannedResourceState::logicalId)
                .containsExactlyElementsOf(expected);
    }

    @Test
    void readsKindFromProviderResource() {
        ScannedResources scanned = new ResourceScanner(GOOD).scan();

        assertThat(resource(scanned, "alphaVpc").kind()).isSameAs(ScanKind.Vpc);
        assertThat(resource(scanned, "betaSubnet").kind()).isSameAs(ScanKind.Subnet);
        assertThat(resource(scanned, "gammaEc2").kind()).isSameAs(ScanKind.Ec2);
        assertThat(resource(scanned, "deltaRds").kind()).isSameAs(ScanKind.Rds);
    }

    // ── 검증 ─────────────────────────────────────────────────────

    @Test
    void rejectsBlankLogicalId() {
        assertThatThrownBy(() -> new ResourceScanner(BAD + ".blank").scan())
                .isInstanceOf(ResourceScanException.class)
                .hasMessageContaining("com.infrastruct.fixture.scan.bad.blank.BlankName");
    }

    @Test
    void rejectsWhitespaceOnlyLogicalId() {
        assertThatThrownBy(() -> new ResourceScanner(BAD + ".whitespace").scan())
                .isInstanceOf(ResourceScanException.class)
                .hasMessageContaining("com.infrastruct.fixture.scan.bad.whitespace.WhitespaceName");
    }

    @Test
    void rejectsLogicalIdWithInnerWhitespace() {
        assertThatThrownBy(() -> new ResourceScanner(BAD + ".inner").scan())
                .isInstanceOf(ResourceScanException.class)
                .hasMessageContaining("com.infrastruct.fixture.scan.bad.inner.InnerSpaceName");
    }

    @Test
    void rejectsResourceThatIsNotProviderResource() {
        assertThatThrownBy(() -> new ResourceScanner(BAD + ".notprovider").scan())
                .isInstanceOf(ResourceScanException.class)
                .hasMessageContaining(
                        "com.infrastruct.fixture.scan.bad.notprovider.NotAProviderResource");
    }

    @Test
    void rejectsResourceWithNullKind() {
        assertThatThrownBy(() -> new ResourceScanner(BAD + ".nokind").scan())
                .isInstanceOf(ResourceScanException.class)
                .hasMessageContaining("com.infrastruct.fixture.scan.bad.nokind.MissingKind");
    }

    @Test
    void rejectsResourceWithoutNoArgConstructor() {
        assertThatThrownBy(() -> new ResourceScanner(BAD + ".noctor").scan())
                .isInstanceOf(ResourceScanException.class)
                .hasMessageContaining("com.infrastruct.fixture.scan.bad.noctor.NoNoArgConstructor");
    }

    @Test
    void keepsReflectiveCauseWhenInstantiationFails() {
        assertThatThrownBy(() -> new ResourceScanner(BAD + ".noctor").scan())
                .hasCauseInstanceOf(ReflectiveOperationException.class);
    }

    @Test
    void namesBothClassesWhenLogicalIdIsDuplicated() {
        assertThatThrownBy(() -> new ResourceScanner(BAD + ".dup").scan())
                .isInstanceOf(ResourceScanException.class)
                .hasMessageContaining("com.infrastruct.fixture.scan.bad.dup.TwinOne")
                .hasMessageContaining("com.infrastruct.fixture.scan.bad.dup.TwinTwo");
    }

    // ── 예외 타입 ────────────────────────────────────────────────

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

    private static ScannedResourceState resource(ScannedResources scanned, String logicalId) {
        return scanned.resources().stream()
                .filter(state -> logicalId.equals(state.logicalId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(logicalId + " 를 찾지 못했다"));
    }
}
