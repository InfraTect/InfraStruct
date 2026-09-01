package com.infrastruct.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.infrastruct.spi.ChangeType;
import com.infrastruct.spi.CurrentResourceState;
import com.infrastruct.spi.DependencyDiff;
import com.infrastruct.spi.DesiredResourceState;
import com.infrastruct.spi.FieldDiff;
import com.infrastruct.spi.Kind;
import com.infrastruct.spi.OrderedResourceChangeSet;
import com.infrastruct.spi.ResourceChange;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlanCreatorTest {

    private static final Kind VPC = () -> "vpc";
    private static final Kind SUBNET = () -> "subnet";
    private static final Kind EC2 = () -> "ec2";

    private final PlanCreator planCreator = new PlanCreator();

    @Test
    void emptyChangeSetProducesEmptyInternalPlan() {
        OrderedResourceChangeSet result = planCreator.createPlan(changes());

        assertThat(result.diffs()).isEmpty();
    }

    @Test
    void createAndUpdatePlaceDependenciesBeforeDependents() {
        ResourceChange ec2 = create(EC2, "ec2", List.of("subnet"));
        ResourceChange vpc = create(VPC, "vpc", List.of());
        ResourceChange subnet = update(SUBNET, "subnet", List.of("vpc"), List.of("vpc"));

        OrderedResourceChangeSet result = planCreator.createPlan(changes(ec2, subnet, vpc));

        assertThat(result.diffs()).containsExactly(vpc, subnet, ec2);
    }

    @Test
    void deletePlacesDependentsBeforeDependenciesUsingCurrentState() {
        ResourceChange vpc = delete(VPC, "vpc", List.of());
        ResourceChange subnet = delete(SUBNET, "subnet", List.of("vpc"));
        ResourceChange ec2 = delete(EC2, "ec2", List.of("subnet"));

        OrderedResourceChangeSet result = planCreator.createPlan(changes(vpc, ec2, subnet));

        assertThat(result.diffs()).containsExactly(ec2, subnet, vpc);
    }

    @Test
    void createAndUpdateAreAppliedBeforeDeletes() {
        ResourceChange deleted = delete(VPC, "old-vpc", List.of());
        ResourceChange created = create(VPC, "new-vpc", List.of());
        ResourceChange updated = update(SUBNET, "subnet", List.of(), List.of());

        OrderedResourceChangeSet result =
                planCreator.createPlan(changes(deleted, updated, created));

        assertThat(result.diffs()).containsExactly(created, updated, deleted);
    }

    @Test
    void independentResourcesAreOrderedByLogicalId() {
        ResourceChange zebra = create(VPC, "zebra", List.of());
        ResourceChange alpha = create(VPC, "alpha", List.of());
        ResourceChange middle = create(VPC, "middle", List.of());

        OrderedResourceChangeSet result = planCreator.createPlan(changes(zebra, alpha, middle));

        assertThat(result.diffs()).containsExactly(alpha, middle, zebra);
    }

    @Test
    void newlyReadyResourceCompetesGloballyByLogicalId() {
        ResourceChange alpha = create(VPC, "alpha", List.of());
        ResourceChange bravo = create(SUBNET, "bravo", List.of("alpha"));
        ResourceChange charlie = create(VPC, "charlie", List.of());

        OrderedResourceChangeSet result = planCreator.createPlan(changes(charlie, bravo, alpha));

        assertThat(result.diffs()).containsExactly(alpha, bravo, charlie);
    }

    @Test
    void dependencyOutsideChangeSetDoesNotBlockPlan() {
        ResourceChange subnet = create(SUBNET, "subnet", List.of("existing-vpc"));

        OrderedResourceChangeSet result = planCreator.createPlan(changes(subnet));

        assertThat(result.diffs()).containsExactly(subnet);
    }

    @Test
    void consecutiveInternalPlansDoNotShareState() {
        ResourceChange first = create(VPC, "first", List.of());
        ResourceChange second = create(SUBNET, "second", List.of());

        planCreator.createPlan(changes(first));
        OrderedResourceChangeSet result = planCreator.createPlan(changes(second));

        assertThat(result.diffs()).containsExactly(second);
    }

    @Test
    void cyclicUpsertDependenciesAreRejected() {
        ResourceChange first = create(VPC, "first", List.of("second"));
        ResourceChange second = create(SUBNET, "second", List.of("first"));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> planCreator.createPlan(changes(first, second)))
                .withMessageContaining("순환");
    }

    @Test
    void cyclicDeleteDependenciesAreRejected() {
        ResourceChange first = delete(VPC, "first", List.of("second"));
        ResourceChange second = delete(SUBNET, "second", List.of("first"));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> planCreator.createPlan(changes(first, second)))
                .withMessageContaining("순환");
    }

    @Test
    void validPlanCanBeCreatedAfterFailedCall() {
        ResourceChange cyclicFirst = create(VPC, "first", List.of("second"));
        ResourceChange cyclicSecond = create(SUBNET, "second", List.of("first"));
        ResourceChange valid = create(EC2, "valid", List.of());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> planCreator.createPlan(changes(cyclicFirst, cyclicSecond)));

        assertThat(planCreator.createPlan(changes(valid)).diffs()).containsExactly(valid);
    }

    @Test
    void duplicateLogicalIdsAreRejectedInsteadOfSilentlyOverwritten() {
        ResourceChange first = create(VPC, "same", List.of());
        ResourceChange duplicate = create(SUBNET, "same", List.of());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> planCreator.createPlan(changes(first, duplicate)))
                .withMessageContaining("same");
    }

    @Test
    void nullChangeSetIsRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> planCreator.createPlan(null))
                .withMessageContaining("changeSet");
    }

    @Test
    void emptyExternalPlanExplainsThatNothingWillChange() {
        assertThat(planCreator.renderExternalPlan(changes()))
                .isEqualTo("No changes. Infrastructure is up-to-date.");
    }

    @Test
    void nullExternalChangeSetIsRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> planCreator.renderExternalPlan(null))
                .withMessageContaining("changeSet");
    }

    @Test
    void externalPlanGroupsChangesAndRendersCreateFromDesiredState() {
        ResourceChange deleted = delete(EC2, "old", List.of());
        ResourceChange updated =
                update(
                        SUBNET,
                        "app",
                        Map.of("cidr", "10.0.1.0/24"),
                        Map.of("cidr", "10.0.2.0/24"),
                        List.of("old-vpc"),
                        List.of("new-vpc"));
        ResourceChange created =
                create(
                        VPC,
                        "main",
                        Map.of("tags", List.of("prod", "shared"), "cidr", "10.0.0.0/16"),
                        List.of());

        String result = planCreator.renderExternalPlan(changes(deleted, updated, created));

        assertThat(result)
                .isEqualTo(
                        """
                        InfraStruct execution plan:
                          (+ create, ~ update, - delete)

                          + vpc.main
                              cidr = "10.0.0.0/16"
                              tags = ["prod", "shared"]

                          ~ subnet.app
                              cidr: "10.0.1.0/24" -> "10.0.2.0/24"
                              depends_on = {new-vpc: null -> new-vpc, old-vpc: old-vpc -> null}

                          - ec2.old

                        Plan: 1 to create, 1 to update, 1 to delete.
                        """);
    }

    @Test
    void externalPlanOrdersEachGroupByLogicalIdRegardlessOfInputOrder() {
        ResourceChange zebra = create(VPC, "zebra", List.of());
        ResourceChange alpha = create(VPC, "alpha", List.of());

        String result = planCreator.renderExternalPlan(changes(zebra, alpha));

        assertThat(result).containsSubsequence("+ vpc.alpha", "+ vpc.zebra");
    }

    @Test
    void consecutiveExternalPlansDoNotReusePreviousChanges() {
        ResourceChange first = create(VPC, "first", List.of());
        ResourceChange second = delete(EC2, "second", List.of());

        planCreator.renderExternalPlan(changes(first));
        String result = planCreator.renderExternalPlan(changes(second));

        assertThat(result).contains("- ec2.second").doesNotContain("vpc.first");
    }

    private static ResourceChangeSet changes(ResourceChange... changes) {
        return new ResourceChangeSet(List.of(changes));
    }

    private static ResourceChange create(Kind kind, String logicalId, List<String> dependencies) {
        return create(kind, logicalId, Map.of(), dependencies);
    }

    private static ResourceChange create(
            Kind kind, String logicalId, Map<String, Object> config, List<String> dependencies) {
        return new ResourceChange(
                ChangeType.CREATE,
                null,
                desired(kind, logicalId, config, dependencies),
                List.of(),
                List.of());
    }

    private static ResourceChange delete(Kind kind, String logicalId, List<String> dependencies) {
        return new ResourceChange(
                ChangeType.DELETE,
                current(kind, logicalId, Map.of(), dependencies),
                null,
                List.of(),
                List.of());
    }

    private static ResourceChange update(
            Kind kind,
            String logicalId,
            List<String> beforeDependencies,
            List<String> afterDependencies) {
        return update(kind, logicalId, Map.of(), Map.of(), beforeDependencies, afterDependencies);
    }

    private static ResourceChange update(
            Kind kind,
            String logicalId,
            Map<String, Object> beforeConfig,
            Map<String, Object> afterConfig,
            List<String> beforeDependencies,
            List<String> afterDependencies) {
        List<FieldDiff> fieldDiffs =
                beforeConfig.equals(afterConfig)
                        ? List.of()
                        : List.of(
                                new FieldDiff(
                                        "cidr",
                                        beforeConfig.get("cidr"),
                                        afterConfig.get("cidr"),
                                        ChangeType.UPDATE));
        List<DependencyDiff> dependencyDiffs =
                beforeDependencies.equals(afterDependencies)
                        ? List.of()
                        : List.of(
                                new DependencyDiff("old-vpc", "old-vpc", null, ChangeType.DELETE),
                                new DependencyDiff("new-vpc", null, "new-vpc", ChangeType.CREATE));
        return new ResourceChange(
                ChangeType.UPDATE,
                current(kind, logicalId, beforeConfig, beforeDependencies),
                desired(kind, logicalId, afterConfig, afterDependencies),
                fieldDiffs,
                dependencyDiffs);
    }

    private static DesiredResourceState desired(
            Kind kind, String logicalId, Map<String, Object> config, List<String> dependencies) {
        return new DesiredResourceState(kind, logicalId, config, dependencies, Set.of());
    }

    private static CurrentResourceState current(
            Kind kind, String logicalId, Map<String, Object> config, List<String> dependencies) {
        return new CurrentResourceState(
                kind, logicalId, config, dependencies, Set.of(), "physical-id");
    }
}
