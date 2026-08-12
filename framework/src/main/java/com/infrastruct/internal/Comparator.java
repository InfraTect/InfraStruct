package com.infrastruct.internal;

import com.infrastruct.spi.ChangeType;
import com.infrastruct.spi.CurrentResourceState;
import com.infrastruct.spi.CurrentResources;
import com.infrastruct.spi.DependencyDiff;
import com.infrastruct.spi.DesiredResourceState;
import com.infrastruct.spi.DesiredResources;
import com.infrastruct.spi.FieldDiff;
import com.infrastruct.spi.ResourceChange;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * {@link DesiredResources}와 {@link CurrentResources}를 logicalId 기준으로 비교해 {@link ResourceChangeSet}을
 * 만든다.
 */
public final class Comparator {

    public ResourceChangeSet compare(
            DesiredResources desiredResources, CurrentResources currentResources) {
        Map<String, DesiredResourceState> desiredById =
                indexByLogicalId(desiredResources.resources(), DesiredResourceState::logicalId);
        Map<String, CurrentResourceState> currentById =
                indexByLogicalId(currentResources.resources(), CurrentResourceState::logicalId);

        List<ResourceChange> changes = new ArrayList<>();

        for (Map.Entry<String, DesiredResourceState> entry : desiredById.entrySet()) {
            String logicalId = entry.getKey();
            DesiredResourceState desired = entry.getValue();
            CurrentResourceState current = currentById.get(logicalId);

            if (current == null) {
                changes.add(
                        new ResourceChange(ChangeType.CREATE, null, desired, List.of(), List.of()));
                continue;
            }

            List<FieldDiff> fieldDiffs = diffConfig(current.config(), desired.config());
            List<DependencyDiff> dependencyDiffs =
                    diffDependencies(current.dependencies(), desired.dependencies());

            if (!fieldDiffs.isEmpty() || !dependencyDiffs.isEmpty()) {
                changes.add(
                        new ResourceChange(
                                ChangeType.UPDATE, current, desired, fieldDiffs, dependencyDiffs));
            }
        }

        for (Map.Entry<String, CurrentResourceState> entry : currentById.entrySet()) {
            if (!desiredById.containsKey(entry.getKey())) {
                changes.add(
                        new ResourceChange(
                                ChangeType.DELETE, entry.getValue(), null, List.of(), List.of()));
            }
        }

        return new ResourceChangeSet(changes);
    }

    private static List<FieldDiff> diffConfig(
            Map<String, Object> currentConfig, Map<String, Object> desiredConfig) {
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(currentConfig.keySet());
        keys.addAll(desiredConfig.keySet());

        List<FieldDiff> diffs = new ArrayList<>();
        for (String key : keys) {
            boolean inCurrent = currentConfig.containsKey(key);
            boolean inDesired = desiredConfig.containsKey(key);
            Object oldValue = currentConfig.get(key);
            Object newValue = desiredConfig.get(key);

            if (inCurrent && !inDesired) {
                diffs.add(new FieldDiff(key, oldValue, null, ChangeType.DELETE));
            } else if (!inCurrent) {
                diffs.add(new FieldDiff(key, null, newValue, ChangeType.CREATE));
            } else if (!Objects.equals(oldValue, newValue)) {
                diffs.add(new FieldDiff(key, oldValue, newValue, ChangeType.UPDATE));
            }
        }
        return diffs;
    }

    private static List<DependencyDiff> diffDependencies(
            List<String> currentDeps, List<String> desiredDeps) {
        Set<String> currentSet = new LinkedHashSet<>(currentDeps);
        Set<String> desiredSet = new LinkedHashSet<>(desiredDeps);

        List<DependencyDiff> diffs = new ArrayList<>();
        for (String dependency : desiredSet) {
            if (!currentSet.contains(dependency)) {
                diffs.add(new DependencyDiff(dependency, null, dependency, ChangeType.CREATE));
            }
        }
        for (String dependency : currentSet) {
            if (!desiredSet.contains(dependency)) {
                diffs.add(new DependencyDiff(dependency, dependency, null, ChangeType.DELETE));
            }
        }
        return diffs;
    }

    private static <T> Map<String, T> indexByLogicalId(
            List<T> states, java.util.function.Function<T, String> logicalIdOf) {
        Map<String, T> byLogicalId = new LinkedHashMap<>();
        for (T state : states) {
            String logicalId = Objects.requireNonNull(logicalIdOf.apply(state), "logicalId");
            if (byLogicalId.putIfAbsent(logicalId, state) != null) {
                throw new IllegalArgumentException("중복된 logicalId: " + logicalId);
            }
        }
        return byLogicalId;
    }
}
