package com.infrastruct.internal;

import com.infrastruct.spi.ChangeType;
import com.infrastruct.spi.DependencyDiff;
import com.infrastruct.spi.FieldDiff;
import com.infrastruct.spi.OrderedResourceChangeSet;
import com.infrastruct.spi.ResourceChange;
import com.infrastruct.spi.ResourceState;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.function.Function;

/**
 * 변경 목록을 의존성에 맞는 실행 순서와 사용자용 출력으로 변환한다.
 *
 * <p>CREATE/UPDATE는 의존 자원을 먼저 처리하고 DELETE는 의존하는 자원을 먼저 제거한다. 계획에 포함되지 않은 의존성은 이미 존재하는 자원일 수 있으므로 정렬
 * 제약에서 제외한다.
 *
 * <p>동일하게 실행 가능한 자원은 logicalId 사전순으로 정렬해 같은 입력이 항상 같은 계획을 만들도록 한다. 중복 logicalId나 순환 의존성처럼 실행 순서를
 * 확정할 수 없는 입력은 계획 생성 실패로 처리한다.
 */
public final class PlanCreator {

    /**
     * 변경 목록을 의존성에 맞는 실행 순서로 정렬한다.
     *
     * @param changeSet 정렬할 변경 목록
     * @return 실행 순서로 정렬된 변경 목록
     */
    public OrderedResourceChangeSet createPlan(ResourceChangeSet changeSet) {
        Objects.requireNonNull(changeSet, "changeSet");     //예외 처리
        Map<String, ResourceChange> changesById = indexByLogicalId(changeSet.diffs());

        List<ResourceChange> upserts =
                changesById.values().stream()
                        .filter(change -> change.type() != ChangeType.DELETE)   //DELETE제외하기
                        .toList();
        List<ResourceChange> deletes =
                changesById.values().stream()
                        .filter(change -> change.type() == ChangeType.DELETE)   //DELETE만 포함하기
                        .toList();

        List<ResourceChange> ordered = new ArrayList<>(changeSet.diffs().size());   //변경 목록 확보
        ordered.addAll(topologicalSort(upserts, change -> change.after().dependencies(), false));
        ordered.addAll(topologicalSort(deletes, change -> change.before().dependencies(), true));
        return new OrderedResourceChangeSet(ordered);
    }

    /**
     * 변경 목록을 사용자가 검토할 수 있는 결정적인 문자열로 렌더링한다.
     *
     * @param changeSet 렌더링할 변경 목록
     * @return 사용자용 실행 계획
     */
    public String renderExternalPlan(ResourceChangeSet changeSet) {
        Objects.requireNonNull(changeSet, "changeSet");
        indexByLogicalId(changeSet.diffs());
        if (changeSet.diffs().isEmpty()) {
            return "No changes. Infrastructure is up-to-date.";
        }

        StringBuilder output =
                new StringBuilder(
                        "InfraStruct execution plan:\n  (+ create, ~ update, - delete)\n");
        appendGroup(output, changeSet.diffs(), ChangeType.CREATE);
        appendGroup(output, changeSet.diffs(), ChangeType.UPDATE);
        appendGroup(output, changeSet.diffs(), ChangeType.DELETE);

        long creates = count(changeSet.diffs(), ChangeType.CREATE);
        long updates = count(changeSet.diffs(), ChangeType.UPDATE);
        long deletes = count(changeSet.diffs(), ChangeType.DELETE);
        output.append('\n')
                .append("Plan: ")
                .append(creates)
                .append(" to create, ")
                .append(updates)
                .append(" to update, ")
                .append(deletes)
                .append(" to delete.\n");
        return output.toString();
    }

    private static Map<String, ResourceChange> indexByLogicalId(List<ResourceChange> changes) {
        Map<String, ResourceChange> changesById = new LinkedHashMap<>();
        for (ResourceChange change : changes) {
            String logicalId = logicalId(change);
            if (changesById.putIfAbsent(logicalId, change) != null) {   //중복 logicalId 차단
                throw new IllegalArgumentException("중복된 logicalId: " + logicalId);
            }
        }
        return changesById;
    }

    private static List<ResourceChange> topologicalSort(
            List<ResourceChange> changes,
            Function<ResourceChange, List<String>> dependenciesOf,
            boolean reverseEdges) {
        Map<String, ResourceChange> changesById = new HashMap<>();
        Map<String, Integer> indegrees = new HashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        for (ResourceChange change : changes) {
            String logicalId = logicalId(change);
            changesById.put(logicalId, change);
            indegrees.put(logicalId, 0);
            outgoing.put(logicalId, new ArrayList<>());
        }

        for (ResourceChange change : changes) {
            String logicalId = logicalId(change);
            for (String dependency : dependenciesOf.apply(change)) {
                if (!changesById.containsKey(dependency)) {
                    continue;
                }
                String from = reverseEdges ? logicalId : dependency;
                String to = reverseEdges ? dependency : logicalId;
                outgoing.get(from).add(to);
                indegrees.compute(to, (ignored, degree) -> degree + 1);
            }
        }

        PriorityQueue<String> ready = new PriorityQueue<>();
        indegrees.forEach(
                (logicalId, degree) -> {
                    if (degree == 0) {
                        ready.add(logicalId);
                    }
                });

        List<ResourceChange> ordered = new ArrayList<>(changes.size());
        while (!ready.isEmpty()) {
            String logicalId = ready.remove();
            ordered.add(changesById.get(logicalId));
            for (String dependent : outgoing.get(logicalId)) {
                int remaining = indegrees.compute(dependent, (ignored, degree) -> degree - 1);
                if (remaining == 0) {
                    ready.add(dependent);
                }
            }
        }
        if (ordered.size() != changes.size()) {
            throw new IllegalArgumentException("순환 의존성으로 실행 계획을 만들 수 없다");
        }
        return ordered;
    }

    private static void appendGroup(
            StringBuilder output, List<ResourceChange> changes, ChangeType type) {
        List<ResourceChange> group =
                changes.stream()
                        .filter(change -> change.type() == type)
                        .sorted(java.util.Comparator.comparing(PlanCreator::logicalId))
                        .toList();
        for (ResourceChange change : group) {
            output.append('\n').append("  ").append(symbol(type)).append(' ');
            ResourceState state = type == ChangeType.DELETE ? change.before() : change.after();
            output.append(state.kind().value()).append('.').append(state.logicalId()).append('\n');
            if (type == ChangeType.CREATE) {
                state.config().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(
                                entry ->
                                        output.append("      ")
                                                .append(entry.getKey())
                                                .append(" = ")
                                                .append(renderValue(entry.getValue()))
                                                .append('\n'));
            } else if (type == ChangeType.UPDATE) {
                appendUpdateDetails(output, change);
            }
        }
    }

    private static void appendUpdateDetails(StringBuilder output, ResourceChange change) {
        change.fieldDiffs().stream()
                .sorted(java.util.Comparator.comparing(FieldDiff::field))
                .forEach(
                        diff ->
                                output.append("      ")
                                        .append(diff.field())
                                        .append(": ")
                                        .append(renderValue(diff.oldValue()))
                                        .append(" -> ")
                                        .append(renderValue(diff.newValue()))
                                        .append('\n'));
        if (!change.dependencyDiffs().isEmpty()) {
            output.append("      depends_on = {");
            List<DependencyDiff> dependencies =
                    change.dependencyDiffs().stream()
                            .sorted(java.util.Comparator.comparing(DependencyDiff::field))
                            .toList();
            for (int index = 0; index < dependencies.size(); index++) {
                if (index > 0) {
                    output.append(", ");
                }
                DependencyDiff diff = dependencies.get(index);
                output.append(diff.field())
                        .append(": ")
                        .append(String.valueOf(diff.oldValue()))
                        .append(" -> ")
                        .append(String.valueOf(diff.newValue()));
            }
            output.append("}\n");
        }
    }

    private static String renderValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> elements = new ArrayList<>();
            iterable.forEach(element -> elements.add(renderValue(element)));
            return "[" + String.join(", ", elements) + "]";
        }
        return "\"" + value + "\"";
    }

    private static String logicalId(ResourceChange change) {
        return change.type() == ChangeType.DELETE
                ? change.before().logicalId()
                : change.after().logicalId();
    }

    private static char symbol(ChangeType type) {
        return switch (type) {
            case CREATE -> '+';
            case UPDATE -> '~';
            case DELETE -> '-';
        };
    }

    private static long count(Collection<ResourceChange> changes, ChangeType type) {
        return changes.stream().filter(change -> change.type() == type).count();
    }
}
