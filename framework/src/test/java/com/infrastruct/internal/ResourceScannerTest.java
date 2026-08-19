package com.infrastruct.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infrastruct.api.Resource;
import com.infrastruct.fixture.scan.Encrypted;
import com.infrastruct.fixture.scan.Plain;
import com.infrastruct.fixture.scan.ScanKind;
import com.infrastruct.fixture.scan.TagHandler;
import com.infrastruct.fixture.scan.Tagged;
import com.infrastruct.fixture.scan.good.GoodEc2;
import com.infrastruct.fixture.scan.good.GoodRds;
import com.infrastruct.fixture.scan.good.GoodSubnet;
import com.infrastruct.fixture.scan.good.GoodVpc;
import com.infrastruct.fixture.scan.good.OtherSubnet;
import com.infrastruct.spi.ScannedResourceState;
import com.infrastruct.spi.ScannedResources;
import java.util.List;
import java.util.Map;
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

    // ── 필드와 참조 추출 ────────────────────────────────────────

    @Test
    void excludesMetaFieldsFromConfig() {
        ScannedResources scanned = new ResourceScanner(GOOD).scan();

        for (ScannedResourceState state : scanned.resources()) {
            assertThat(state.config()).doesNotContainKeys("kind", "provider");
        }
    }

    @Test
    void putsScalarFieldsIntoConfig() {
        ScannedResources scanned = new ResourceScanner(GOOD).scan();

        assertThat(resource(scanned, "betaSubnet").config())
                .containsEntry("cidrBlock", "10.0.1.0/24");
        assertThat(resource(scanned, "gammaEc2").config())
                .containsEntry("instanceType", "t3.micro");
    }

    @Test
    void readsGrandparentFields() {
        ScannedResources scanned = new ResourceScanner(GOOD).scan();

        assertThat(resource(scanned, "alphaVpc").config()).containsEntry("owner", "infra-team");
    }

    @Test
    void childFieldWinsOverShadowedParentField() {
        ScannedResources scanned = new ResourceScanner(GOOD).scan();

        assertThat(resource(scanned, "gammaEc2").config()).containsEntry("owner", "team-b");
    }

    @Test
    void omitsNullFieldsFromConfig() {
        ScannedResources scanned = new ResourceScanner(GOOD).scan();

        assertThat(resource(scanned, "betaSubnet").config()).doesNotContainKey("az");
    }

    @Test
    void putsMapValuedFieldIntoConfig() {
        ScannedResources scanned = new ResourceScanner(GOOD).scan();

        assertThat(resource(scanned, "gammaEc2").config())
                .containsEntry("tags", Map.of("team", "infra"));
    }

    @Test
    void collectsRequiredFieldNames() {
        ScannedResources scanned = new ResourceScanner(GOOD).scan();

        assertThat(resource(scanned, "betaSubnet").requiredFields())
                .containsExactlyInAnyOrder("vpc", "cidrBlock");
    }

    @Test
    void putsSingleClassReferenceIntoDependencies() {
        ScannedResources scanned = new ResourceScanner(GOOD).scan();

        assertThat(resource(scanned, "betaSubnet").dependencies()).containsExactly("alphaVpc");
        assertThat(resource(scanned, "betaSubnet").config()).doesNotContainKey("vpc");
    }

    @Test
    void flattensCollectionReferenceElements() {
        ScannedResources scanned = new ResourceScanner(GOOD).scan();

        assertThat(resource(scanned, "deltaRds").dependencies())
                .containsExactly("betaSubnet", "epsilonSubnet");
        assertThat(resource(scanned, "deltaRds").config()).doesNotContainKey("subnets");
    }

    @Test
    void ordersDependenciesByFieldName() {
        ScannedResources scanned = new ResourceScanner(GOOD).scan();

        // 필드명 사전순: subnet(→ betaSubnet) 이 vpc(→ alphaVpc) 보다 앞이다. logicalId
        // 사전순이었다면 alphaVpc 가 앞이므로, 이 단언이 필드명 기준 정렬을 반증한다.
        assertThat(resource(scanned, "gammaEc2").dependencies())
                .containsExactly("betaSubnet", "alphaVpc");
    }

    @Test
    void rejectsClassReferenceWithoutResourceAnnotation() {
        assertThatThrownBy(() -> new ResourceScanner(BAD + ".dangling").scan())
                .isInstanceOf(ResourceScanException.class)
                .hasMessageContaining("com.infrastruct.fixture.scan.bad.dangling.DanglingReference")
                .hasMessageContaining("com.infrastruct.fixture.scan.bad.dangling.NotAResourceVpc");
    }

    // ── 매크로 annotation 포착 ──────────────────────────────────

    @Test
    void capturesOnlyBehaviorAnnotations() {
        ScannedResources scanned = new ResourceScanner(GOOD).scan();
        List<CapturedAnnotation> captured = resource(scanned, "betaSubnet").capturedAnnotations();

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).handlerClass()).isEqualTo(TagHandler.class);
    }

    @Test
    void keepsAnnotationInstanceWithMemberValues() {
        ScannedResources scanned = new ResourceScanner(GOOD).scan();
        CapturedAnnotation captured = resource(scanned, "betaSubnet").capturedAnnotations().get(0);

        assertThat(((Tagged) captured.anno()).value()).isEqualTo("net");
    }

    @Test
    void ordersCapturedAnnotationsByTypeName() {
        ScannedResources scanned = new ResourceScanner(GOOD).scan();

        // 선언 순서는 @Tagged @Encrypted 다. type 이름 사전순 정렬이 실제로 동작해야 뒤집힌다.
        assertThat(resource(scanned, "gammaEc2").capturedAnnotations())
                .<Class<?>>extracting(captured -> captured.anno().annotationType())
                .containsExactly(Encrypted.class, Tagged.class);
    }

    @Test
    void doesNotCaptureAnnotationWithoutBehavior() {
        ScannedResources scanned = new ResourceScanner(GOOD).scan();

        assertThat(resource(scanned, "betaSubnet").capturedAnnotations())
                .<Class<?>>extracting(captured -> captured.anno().annotationType())
                .doesNotContain(Plain.class);
    }

    private static ScannedResourceState resource(ScannedResources scanned, String logicalId) {
        return scanned.resources().stream()
                .filter(state -> logicalId.equals(state.logicalId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(logicalId + " 를 찾지 못했다"));
    }
}
