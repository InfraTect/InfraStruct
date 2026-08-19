package com.infrastruct.spi;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 사용자가 원하는 자원 상태의 유효성을 검증하는 공통 기반 클래스.
 *
 * <p>공통 검증은 프레임워크가 제공하고, 프로바이더별 검증은 하위 클래스가 구현한다. 두 검증은 단축 평가하지 않고 모두 실행해 가능한 위반을 한 번에 반환한다.
 */
public abstract class Validator {

    private static final String MISSING_KIND = "MISSING_KIND";
    private static final String INVALID_KIND_VALUE = "INVALID_KIND_VALUE";
    private static final String MISSING_LOGICAL_ID = "MISSING_LOGICAL_ID";
    private static final String DUPLICATE_LOGICAL_ID = "DUPLICATE_LOGICAL_ID";
    private static final String INVALID_REQUIRED_FIELD_NAME = "INVALID_REQUIRED_FIELD_NAME";
    private static final String CYCLIC_DEPENDENCY = "CYCLIC_DEPENDENCY";

    /** DFS 중 현재 정점의 방문 상태. Map에 상태가 없으면 아직 방문하지 않은 정점이다. */
    private enum VisitState {
        VISITING,
        VISITED
    }

    /**
     * 공통 검증과 프로바이더별 검증을 모두 수행한다.
     *
     * @param desiredResources 검증할 최종 자원 상태 전체
     * @return 두 검증에서 발견한 위반을 모두 합친 결과
     * @throws NullPointerException {@code desiredResources}가 {@code null}인 경우
     */
    public final ValidationResult validate(DesiredResources desiredResources) {//common과 provider둘의 violation들을 합침.
        Objects.requireNonNull(desiredResources, "desiredResources");

        ValidationResult common = validateCommon(desiredResources);
        ValidationResult provider =
                Objects.requireNonNull(
                        validateProviderResource(desiredResources),
                        "validateProviderResource result");
        return common.merge(provider);
    }

    /**
     * 모든 프로바이더에 적용되는 공통 규칙을 검증한다.
     *
     * <p>kind와 logicalId, logicalId의 전체 고유성, 필수 필드 이름, dependency 순환을 검사한다. 서로 독립적인 오류는 중간에 멈추지 않고
     * 모두 수집한다.
     *
     * @param desiredResources 검증할 최종 자원 상태 전체
     * @return 공통 규칙에서 발견한 위반 목록
     * @throws NullPointerException {@code desiredResources}가 {@code null}인 경우
     */
    protected final ValidationResult validateCommon(DesiredResources desiredResources) {
        Objects.requireNonNull(desiredResources, "desiredResources");
        List<Violation> violations = new ArrayList<>();

        validateResourceIdentities(desiredResources, violations);
        Map<String, DesiredResourceState> resourcesById =
                indexByLogicalId(desiredResources, violations);
        validateRequiredFieldNames(desiredResources, violations);
        validateNoDependencyCycles(resourcesById, violations);

        return new ValidationResult(violations);
    }

    /**
     * 프로바이더 고유 규칙을 검증한다.
     *
     * <p>기본 구현은 위반이 없는 결과를 돌려준다. 프로바이더는 이 메서드를 재정의해 Kind별 설정값과 자원 간 호환성을 검사한다.
     *
     * @param desiredResources 검증할 최종 자원 상태 전체
     * @return 프로바이더 규칙에서 발견한 위반 목록
     */
    protected ValidationResult validateProviderResource(DesiredResources desiredResources) {
        return ValidationResult.valid();
    }//provider에서 구현 할 것임.

    private static void validateResourceIdentities(
            DesiredResources desiredResources, List<Violation> violations) {
        for (DesiredResourceState resource : desiredResources.resources()) {
            if (resource.kind() == null) {
                violations.add(
                        new Violation(MISSING_KIND, resource.logicalId(), "kind", "kind가 없습니다"));
            } else {
                String kindValue = resource.kind().value();
                if (isBlank(kindValue)) {
                    violations.add(
                            new Violation(
                                    INVALID_KIND_VALUE,
                                    resource.logicalId(),
                                    "kind",
                                    "kind.value()가 비어 있습니다"));
                }
            }

            if (isBlank(resource.logicalId())) {
                violations.add(
                        new Violation(
                                MISSING_LOGICAL_ID,
                                resource.logicalId(),
                                "logicalId",
                                "logicalId가 비어 있습니다"));
            }
        }
    }

    private static Map<String, DesiredResourceState> indexByLogicalId(
            DesiredResources desiredResources, List<Violation> violations) {
        Map<String, DesiredResourceState> resourcesById = new LinkedHashMap<>();
        for (DesiredResourceState resource : desiredResources.resources()) {
            String logicalId = resource.logicalId();
            if (isBlank(logicalId)) {
                continue;
            }
            if (resourcesById.putIfAbsent(logicalId, resource) != null) {
                violations.add(
                        new Violation(
                                DUPLICATE_LOGICAL_ID,
                                logicalId,
                                "logicalId",
                                "logicalId가 중복되었습니다: " + logicalId));
            }
        }
        return resourcesById;
    }

    private static void validateRequiredFieldNames(
            DesiredResources desiredResources, List<Violation> violations) {
        for (DesiredResourceState resource : desiredResources.resources()) {
            for (String requiredField : resource.requiredFields()) {
                if (requiredField.isBlank()) {
                    violations.add(
                            new Violation(
                                    INVALID_REQUIRED_FIELD_NAME,
                                    resource.logicalId(),
                                    "requiredFields",
                                    "필수 필드 이름이 비어 있습니다"));
                }
            }
        }
    }

    private static void validateNoDependencyCycles(
            Map<String, DesiredResourceState> resourcesById, List<Violation> violations) {
        Map<String, VisitState> states = new HashMap<>();
        Deque<String> path = new ArrayDeque<>();

        for (String logicalId : resourcesById.keySet()) {
            if (!states.containsKey(logicalId)) {
                visit(logicalId, resourcesById, states, path, violations);
            }
        }
    }

    private static void visit(
            String logicalId,
            Map<String, DesiredResourceState> resourcesById,
            Map<String, VisitState> states,
            Deque<String> path,
            List<Violation> violations) {
        states.put(logicalId, VisitState.VISITING);
        path.addLast(logicalId);

        for (String targetId : resourcesById.get(logicalId).dependencies()) {
            if (!resourcesById.containsKey(targetId)) {
                continue;
            }

            VisitState targetState = states.get(targetId);
            if (targetState == VisitState.VISITING) {
                violations.add(cyclicDependency(path, targetId));
                continue;
            }
            if (targetState == null) {
                visit(targetId, resourcesById, states, path, violations);
            }
        }

        path.removeLast();
        states.put(logicalId, VisitState.VISITED);
    }

    private static Violation cyclicDependency(Deque<String> path, String targetId) {
        List<String> cycle = new ArrayList<>();
        boolean inCycle = false;
        for (String logicalId : path) {
            if (logicalId.equals(targetId)) {
                inCycle = true;
            }
            if (inCycle) {
                cycle.add(logicalId);
            }
        }
        cycle.add(targetId);

        return new Violation(
                CYCLIC_DEPENDENCY,
                targetId,
                null,
                "순환 dependency가 발견되었습니다: " + String.join(" -> ", cycle));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
