package com.infrastruct.internal;

import com.infrastruct.api.Resource;
import com.infrastruct.spi.ScannedResources;
import java.util.List;

/**
 * 사용자가 {@link Resource} 로 선언한 클래스를 reflection 으로 읽어 {@link ScannedResources} 로 바꾸는 내부 모듈.
 *
 * <p>파이프라인의 입구다. 여기서 나온 결과가 {@link DesiredStateCreator} 의 입력이 된다.
 *
 * <p>스캐너는 <b>발견해서 옮겨 담을 뿐 해석하지 않는다.</b> 매크로 annotation 은 "아직 소비되지 않은 지시서"로 모아만 두고, 실제로 config 에
 * 반영하는 것은 다음 단계의 몫이다.
 *
 * <p><b>현재 상태: 뼈대(스텁)다.</b> 공개 시그니처만 확정하고 본문은 비워, 다른 사람이 이 타입을 import 해 호출부를 먼저 엮을 수 있게 한다. 실제 스캔
 * 로직은 다음 두 feature 에서 채운다 ({@code plan.md} §1-1).
 *
 * <p>주의: 같은 패키지에 {@link Comparator} 가 있어 simple name {@code Comparator} 는 그쪽으로 잡힌다. 정렬에는 {@code
 * java.util.Comparator} 를 full qualify 해서 써야 한다.
 */
public final class ResourceScanner {

    private final String basePackage;

    /** classpath 전체를 스캔한다. */
    public ResourceScanner() {
        this(null);
    }

    /**
     * 주어진 package 아래만 스캔한다.
     *
     * <p>test 에서 정상 fixture 와 깨진 fixture 를 서로 다른 package 에 격리해 두고 골라 스캔하기 위해, 그리고 나중에 {@code
     * InfraStruct.run(mainClass)} 가 {@code mainClass.getPackageName()} 을 넘겨 사용자 코드만 스캔하게 하기 위해 열어
     * 둔다 ({@code plan.md} §3).
     *
     * @param basePackage 스캔 범위. {@code null} 또는 공백이면 classpath 전체를 스캔한다
     */
    public ResourceScanner(String basePackage) {
        this.basePackage = basePackage;
    }

    /**
     * {@link Resource} 가 붙은 클래스를 모두 찾아 스캔 결과로 바꾼다.
     *
     * <p>원래 의도: classgraph 로 {@link Resource} 가 붙은 클래스를 모아 FQCN 순으로 정렬하고, 각 클래스를 인스턴스화해 필드를 자식에서 부모
     * 방향으로 순회하며 config, dependencies, requiredFields 를 채운다. 매크로 annotation 은 {@code @Behavior} 가 달린
     * 것만 골라 type 이름순으로 모은다. 자세한 알고리즘은 {@code plan.md} §8.
     *
     * <p>아직 본문은 비어 있고 빈 결과를 돌려준다. 채우는 순서는 {@code plan.md} §1-1 의 PR 분할을 따른다.
     *
     * @return 스캔 결과 (스텁: 원소 없는 {@link ScannedResources})
     * @throws ResourceScanException 자원 선언이 잘못되어 스캔을 진행할 수 없는 경우 (스텁 상태에서는 던지지 않는다)
     */
    public ScannedResources scan() {
        // TODO(2번 PR): classgraph 로 @Resource 클래스 수집 → FQCN 정렬 → logicalId 검증 →
        //               인스턴스화 → kind 추출 → logicalId 중복 검사. plan.md §7-D, §7-E, §9.
        // TODO(3번 PR): 필드 순회(자식 → 부모)로 config / dependencies / requiredFields 채우기
        //               + @Behavior 매크로 annotation 포착. plan.md §7-B, §7-C, §7-F.
        return new ScannedResources(List.of());
    }

    /**
     * 스캔 범위로 지정된 package 를 돌려준다.
     *
     * <p>스텁 단계에서 생성자 인자가 실제로 보관되는지 확인할 수 있게 열어 둔다. 본문이 채워지면 {@code findAnnotatedClasses()} 가 이 값을
     * classgraph 의 package 필터로 넘긴다.
     *
     * @return 스캔 범위. 전체 스캔이면 {@code null}
     */
    String basePackage() {
        return basePackage;
    }
}
