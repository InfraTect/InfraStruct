package com.infrastruct.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@link Validator}의 공통 검증 계약을 검증한다. */
class ValidatorTest {

    private static final Kind TEST_KIND = () -> "test-kind";

    private final TestValidator validator = new TestValidator();

    /** 픽스처: 공통 검증 노출과 프로바이더 결과 기록을 맡는 최소 구현체. */
    private static final class TestValidator extends Validator {

        private int providerCalls;
        private ValidationResult providerResult = ValidationResult.valid();

        ValidationResult validateOnlyCommon(DesiredResources desiredResources) {
            return validateCommon(desiredResources);
        }

        @Override
        protected ValidationResult validateProviderResource(DesiredResources desiredResources) {
            providerCalls++;
            return providerResult;
        }
    }

    @Test
    void validDesiredResourcesReturnValidResult() {
        DesiredResourceState resource =
                resource(
                        TEST_KIND,
                        "vpc.main",
                        Map.of("cidrBlock", "10.0.0.0/16"),
                        List.of(),
                        Set.of("cidrBlock"));

        ValidationResult result =
                validator.validateOnlyCommon(new DesiredResources(List.of(resource)));

        assertThat(result.isValid()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void nullDesiredResourcesAreRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> validator.validateOnlyCommon(null))
                .withMessageContaining("desiredResources");
    }

    @Test
    void missingKindProducesViolation() {
        DesiredResourceState resource = resource(null, "vpc.main", Map.of(), List.of(), Set.of());

        ValidationResult result =
                validator.validateOnlyCommon(new DesiredResources(List.of(resource)));

        assertThat(result.isValid()).isFalse();
        assertThat(result.violations())
                .singleElement()
                .satisfies(
                        violation -> {
                            assertThat(violation.logicalId()).isEqualTo("vpc.main");
                            assertThat(violation.field()).isEqualTo("kind");
                            assertThat(violation.message()).contains("kind");
                        });
    }

    @Test
    void nullKindValueProducesViolation() {
        DesiredResourceState resource =
                resource(() -> null, "vpc.main", Map.of(), List.of(), Set.of());

        ValidationResult result =
                validator.validateOnlyCommon(new DesiredResources(List.of(resource)));

        assertThat(result.violations())
                .singleElement()
                .satisfies(violation -> assertThat(violation.message()).contains("kind"));
    }

    @Test
    void blankKindValueProducesViolation() {
        DesiredResourceState resource =
                resource(() -> " ", "vpc.main", Map.of(), List.of(), Set.of());

        ValidationResult result =
                validator.validateOnlyCommon(new DesiredResources(List.of(resource)));

        assertThat(result.violations())
                .singleElement()
                .satisfies(violation -> assertThat(violation.message()).contains("kind"));
    }

    @Test
    void nullLogicalIdProducesViolation() {
        DesiredResourceState resource = resource(TEST_KIND, null, Map.of(), List.of(), Set.of());

        ValidationResult result =
                validator.validateOnlyCommon(new DesiredResources(List.of(resource)));

        assertThat(result.violations())
                .singleElement()
                .satisfies(
                        violation -> {
                            assertThat(violation.logicalId()).isNull();
                            assertThat(violation.field()).isEqualTo("logicalId");
                            assertThat(violation.message()).contains("logicalId");
                        });
    }

    @Test
    void blankLogicalIdProducesViolation() {
        DesiredResourceState resource = resource(TEST_KIND, " ", Map.of(), List.of(), Set.of());

        ValidationResult result =
                validator.validateOnlyCommon(new DesiredResources(List.of(resource)));

        assertThat(result.violations())
                .singleElement()
                .satisfies(violation -> assertThat(violation.message()).contains("logicalId"));
    }

    @Test
    void duplicateLogicalIdsAcrossKindsProduceViolation() {
        DesiredResourceState first = resource(() -> "vpc", "shared", Map.of(), List.of(), Set.of());
        DesiredResourceState second =
                resource(() -> "subnet", "shared", Map.of(), List.of(), Set.of());

        ValidationResult result =
                validator.validateOnlyCommon(new DesiredResources(List.of(first, second)));

        assertThat(result.violations())
                .singleElement()
                .satisfies(
                        violation -> {
                            assertThat(violation.logicalId()).isEqualTo("shared");
                            assertThat(violation.message()).contains("중복");
                        });
    }

    @Test
    void blankRequiredFieldNameProducesViolation() {
        DesiredResourceState resource =
                resource(
                        TEST_KIND,
                        "vpc.main",
                        Map.of("cidrBlock", "10.0.0.0/16"),
                        List.of(),
                        Set.of(" "));

        ValidationResult result =
                validator.validateOnlyCommon(new DesiredResources(List.of(resource)));

        assertThat(result.violations())
                .singleElement()
                .satisfies(violation -> assertThat(violation.message()).contains("필수 필드"));
    }

    @Test
    void acyclicDiamondGraphReturnsValidResult() {
        DesiredResourceState root = resource("root", List.of());
        DesiredResourceState left = resource("left", List.of("root"));
        DesiredResourceState right = resource("right", List.of("root"));
        DesiredResourceState leaf = resource("leaf", List.of("left", "right"));

        ValidationResult result =
                validator.validateOnlyCommon(
                        new DesiredResources(List.of(root, left, right, leaf)));

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void selfDependencyProducesCycleViolation() {
        DesiredResourceState resource = resource("a", List.of("a"));

        ValidationResult result =
                validator.validateOnlyCommon(new DesiredResources(List.of(resource)));

        assertThat(result.violations())
                .singleElement()
                .satisfies(
                        violation -> {
                            assertThat(violation.logicalId()).isEqualTo("a");
                            assertThat(violation.message()).contains("a -> a");
                        });
    }

    @Test
    void indirectDependencyCycleProducesViolationWithPath() {
        DesiredResourceState first = resource("a", List.of("b"));
        DesiredResourceState second = resource("b", List.of("c"));
        DesiredResourceState third = resource("c", List.of("a"));

        ValidationResult result =
                validator.validateOnlyCommon(new DesiredResources(List.of(first, second, third)));

        assertThat(result.violations())
                .singleElement()
                .satisfies(
                        violation -> assertThat(violation.message()).contains("a", "b", "c", "순환"));
    }

    @Test
    void cycleInDisconnectedComponentProducesViolation() {
        DesiredResourceState root = resource("root", List.of());
        DesiredResourceState leaf = resource("leaf", List.of("root"));
        DesiredResourceState firstCycleNode = resource("x", List.of("y"));
        DesiredResourceState secondCycleNode = resource("y", List.of("x"));

        ValidationResult result =
                validator.validateOnlyCommon(
                        new DesiredResources(List.of(root, leaf, firstCycleNode, secondCycleNode)));

        assertThat(result.violations())
                .singleElement()
                .satisfies(violation -> assertThat(violation.message()).contains("x", "y"));
    }

    @Test
    void unknownDependencyTargetIsIgnoredByCycleValidation() {
        DesiredResourceState resource = resource("a", List.of("not-yet-declared"));

        ValidationResult result =
                validator.validateOnlyCommon(new DesiredResources(List.of(resource)));

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void multipleViolationsOnOneResourceArePreserved() {
        DesiredResourceState resource = resource(null, " ", Map.of(), List.of(), Set.of(" "));

        ValidationResult result =
                validator.validateOnlyCommon(new DesiredResources(List.of(resource)));

        assertThat(result.violations()).hasSize(3);
        assertThat(result.violations()).extracting(Violation::logicalId).containsOnly(" ");
    }

    @Test
    void validateMergesCommonAndProviderViolationsWithoutShortCircuiting() {
        DesiredResourceState resource = resource(TEST_KIND, " ", Map.of(), List.of(), Set.of());
        Violation providerViolation =
                new Violation(
                        "PROVIDER_ERROR",
                        "provider.resource",
                        "config",
                        "provider validation failed");
        validator.providerResult = new ValidationResult(List.of(providerViolation));

        ValidationResult result = validator.validate(new DesiredResources(List.of(resource)));

        assertThat(validator.providerCalls).isEqualTo(1);
        assertThat(result.violations()).hasSize(2);
        assertThat(result.violations()).contains(providerViolation);
    }

    private static DesiredResourceState resource(String logicalId, List<String> dependencies) {
        return resource(TEST_KIND, logicalId, Map.of(), dependencies, Set.of());
    }

    private static DesiredResourceState resource(
            Kind kind,
            String logicalId,
            Map<String, Object> config,
            List<String> dependencies,
            Set<String> requiredFields) {
        return new DesiredResourceState(kind, logicalId, config, dependencies, requiredFields);
    }
}
