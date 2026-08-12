package com.infrastruct.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.infrastruct.spi.ChangeType;
import com.infrastruct.spi.CurrentResourceState;
import com.infrastruct.spi.CurrentResources;
import com.infrastruct.spi.DependencyDiff;
import com.infrastruct.spi.DesiredResourceState;
import com.infrastruct.spi.DesiredResources;
import com.infrastruct.spi.FieldDiff;
import com.infrastruct.spi.Kind;
import com.infrastruct.spi.ResourceChange;
import com.infrastruct.spi.ResourceChangeSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ComparatorTest {

    private static final Kind TEST_KIND = () -> "test-kind";

    private final Comparator comparator = new Comparator();

    @Test
    void resourceOnlyInDesiredIsCreate() {
        DesiredResourceState desired =
                desired("vpc.myVpc", Map.of("cidrBlock", "10.0.0.0/16"), List.of());

        ResourceChangeSet result =
                comparator.compare(
                        new DesiredResources(List.of(desired)), new CurrentResources(List.of()));

        assertThat(result.diffs()).hasSize(1);
        ResourceChange change = result.diffs().get(0);
        assertThat(change.type()).isEqualTo(ChangeType.CREATE);
        assertThat(change.before()).isNull();
        assertThat(change.after()).isEqualTo(desired);
        assertThat(change.fieldDiffs()).isEmpty();
        assertThat(change.dependencyDiffs()).isEmpty();
    }

    @Test
    void resourceOnlyInCurrentIsDelete() {
        CurrentResourceState current =
                current("ec2.oldEc2", Map.of("ami", "ubuntu"), List.of(), "i-12345");

        ResourceChangeSet result =
                comparator.compare(
                        new DesiredResources(List.of()), new CurrentResources(List.of(current)));

        assertThat(result.diffs()).hasSize(1);
        ResourceChange change = result.diffs().get(0);
        assertThat(change.type()).isEqualTo(ChangeType.DELETE);
        assertThat(change.before()).isEqualTo(current);
        assertThat(change.after()).isNull();
        assertThat(change.fieldDiffs()).isEmpty();
        assertThat(change.dependencyDiffs()).isEmpty();
    }

    @Test
    void identicalResourceProducesNoChange() {
        DesiredResourceState desired =
                desired("vpc.myVpc", Map.of("cidrBlock", "10.0.0.0/16"), List.of());
        CurrentResourceState current =
                current("vpc.myVpc", Map.of("cidrBlock", "10.0.0.0/16"), List.of(), "vpc-1");

        ResourceChangeSet result =
                comparator.compare(
                        new DesiredResources(List.of(desired)),
                        new CurrentResources(List.of(current)));

        assertThat(result.diffs()).isEmpty();
    }

    @Test
    void changedFieldValueProducesUpdateWithFieldDiff() {
        DesiredResourceState desired =
                desired("vpc.myVpc", Map.of("cidrBlock", "10.0.0.0/20"), List.of());
        CurrentResourceState current =
                current("vpc.myVpc", Map.of("cidrBlock", "10.0.0.0/16"), List.of(), "vpc-1");

        ResourceChangeSet result =
                comparator.compare(
                        new DesiredResources(List.of(desired)),
                        new CurrentResources(List.of(current)));

        assertThat(result.diffs()).hasSize(1);
        ResourceChange change = result.diffs().get(0);
        assertThat(change.type()).isEqualTo(ChangeType.UPDATE);
        assertThat(change.fieldDiffs())
                .containsExactly(
                        new FieldDiff(
                                "cidrBlock", "10.0.0.0/16", "10.0.0.0/20", ChangeType.UPDATE));
        assertThat(change.dependencyDiffs()).isEmpty();
    }

    @Test
    void addedAndRemovedConfigKeysProduceCreateAndDeleteFieldDiffs() {
        DesiredResourceState desired =
                desired("subnet.mySubnet", Map.of("mapPublicIp", true), List.of());
        CurrentResourceState current =
                current(
                        "subnet.mySubnet",
                        Map.of("cidrBlock", "10.0.1.0/24"),
                        List.of(),
                        "subnet-1");

        ResourceChangeSet result =
                comparator.compare(
                        new DesiredResources(List.of(desired)),
                        new CurrentResources(List.of(current)));

        assertThat(result.diffs()).hasSize(1);
        assertThat(result.diffs().get(0).fieldDiffs())
                .containsExactlyInAnyOrder(
                        new FieldDiff("cidrBlock", "10.0.1.0/24", null, ChangeType.DELETE),
                        new FieldDiff("mapPublicIp", null, true, ChangeType.CREATE));
    }

    @Test
    void addedAndRemovedDependenciesProduceDependencyDiffs() {
        DesiredResourceState desired = desired("subnet.mySubnet", Map.of(), List.of("vpc.newVpc"));
        CurrentResourceState current =
                current("subnet.mySubnet", Map.of(), List.of("vpc.oldVpc"), "subnet-1");

        ResourceChangeSet result =
                comparator.compare(
                        new DesiredResources(List.of(desired)),
                        new CurrentResources(List.of(current)));

        assertThat(result.diffs()).hasSize(1);
        assertThat(result.diffs().get(0).dependencyDiffs())
                .containsExactlyInAnyOrder(
                        new DependencyDiff("vpc.oldVpc", "vpc.oldVpc", null, ChangeType.DELETE),
                        new DependencyDiff("vpc.newVpc", null, "vpc.newVpc", ChangeType.CREATE));
    }

    @Test
    void mixOfCreateUpdateDeleteAreAllReported() {
        DesiredResourceState createdVpc =
                desired("vpc.newVpc", Map.of("cidrBlock", "10.0.0.0/16"), List.of());
        DesiredResourceState updatedSubnet =
                desired("subnet.mySubnet", Map.of("cidrBlock", "10.0.1.0/25"), List.of());
        CurrentResourceState currentSubnet =
                current(
                        "subnet.mySubnet",
                        Map.of("cidrBlock", "10.0.1.0/24"),
                        List.of(),
                        "subnet-1");
        CurrentResourceState deletedEc2 =
                current("ec2.oldEc2", Map.of("ami", "ubuntu"), List.of(), "i-1");

        ResourceChangeSet result =
                comparator.compare(
                        new DesiredResources(List.of(createdVpc, updatedSubnet)),
                        new CurrentResources(List.of(currentSubnet, deletedEc2)));

        assertThat(result.diffs())
                .extracting(ResourceChange::type)
                .containsExactlyInAnyOrder(ChangeType.CREATE, ChangeType.UPDATE, ChangeType.DELETE);
    }

    private static DesiredResourceState desired(
            String logicalId, Map<String, Object> config, List<String> deps) {
        return new DesiredResourceState(TEST_KIND, logicalId, config, deps, Set.of());
    }

    private static CurrentResourceState current(
            String logicalId, Map<String, Object> config, List<String> deps, String physicalId) {
        return new CurrentResourceState(TEST_KIND, logicalId, config, deps, Set.of(), physicalId);
    }
}
