package com.infrastruct.internal;

import com.infrastruct.api.Resource;
import com.infrastruct.spi.Kind;
import com.infrastruct.spi.ProviderResource;
import com.infrastruct.spi.ScannedResourceState;
import com.infrastruct.spi.ScannedResources;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 사용자가 {@link Resource} 로 선언한 클래스를 reflection 으로 읽어 {@link ScannedResources} 로 바꾸는 내부 모듈.
 *
 * <p>파이프라인의 입구다. 여기서 나온 결과가 {@link DesiredStateCreator} 의 입력이 된다.
 *
 * <p>스캐너는 <b>발견해서 옮겨 담을 뿐 해석하지 않는다.</b> 매크로 annotation 은 "아직 소비되지 않은 지시서"로 모아만 두고, 실제로 config 에
 * 반영하는 것은 다음 단계의 몫이다.
 *
 * <p><b>현재 상태: 발견과 검증까지 채웠다.</b> logicalId, {@code kind}, 자원 목록의 순서와 에러 6종이 여기서 닫힌다. 필드 순회로 {@code
 * config}, {@code dependencies}, {@code requiredFields} 를 채우고 매크로 annotation 을 포착하는 것은 다음 PR 이다
 * ({@code plan.md} §1-1).
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
     * <p>결과 순서는 클래스 FQCN 순으로 고정한다. classpath 스캔 순서는 환경에 따라 달라지므로, 고정하지 않으면 CI 에서만 깨지는 test 가 생긴다
     * ({@code plan.md} §7-A).
     *
     * @return 스캔 결과
     * @throws ResourceScanException 자원 선언이 잘못되어 스캔을 진행할 수 없는 경우
     */
    public ScannedResources scan() {
        List<ScannedResourceState> states = new ArrayList<>();
        Map<String, Class<?>> declaredBy = new LinkedHashMap<>();

        for (Class<?> type : findAnnotatedClasses()) {
            ScannedResourceState state = scanOne(type);

            // 중복은 Comparator.indexByLogicalId 도 잡지만 그건 비교 단계다. 서로 다른 두 클래스가 같은 name 을
            // 쓴 것은 스캔 시점에 이미 알 수 있다 (plan.md §7-E).
            Class<?> previous = declaredBy.putIfAbsent(state.logicalId(), type);
            if (previous != null) {
                throw new ResourceScanException(
                        "logicalId \""
                                + state.logicalId()
                                + "\" 가 중복이다: "
                                + previous.getName()
                                + " 와 "
                                + type.getName()
                                + ". 둘 중 하나의 @Resource(name) 을 바꿔야 한다.");
            }
            states.add(state);
        }

        return new ScannedResources(states);
    }

    /**
     * 스캔 범위로 지정된 package 를 돌려준다.
     *
     * @return 스캔 범위. 전체 스캔이면 {@code null}
     */
    String basePackage() {
        return basePackage;
    }

    /**
     * {@link Resource} 가 붙은 클래스를 모아 FQCN 순으로 정렬해 돌려준다.
     *
     * @return 정렬된 자원 클래스 목록
     */
    private List<Class<?>> findAnnotatedClasses() {
        ClassGraph graph = new ClassGraph().enableClassInfo().enableAnnotationInfo();
        if (basePackage != null && !basePackage.isBlank()) {
            graph = graph.acceptPackages(basePackage);
        }

        try (ScanResult result = graph.scan()) {
            List<Class<?>> types =
                    new ArrayList<>(result.getClassesWithAnnotation(Resource.class).loadClasses());
            types.sort(java.util.Comparator.comparing(Class::getName));
            return types;
        }
    }

    /**
     * 자원 클래스 하나를 스캔 결과로 바꾼다.
     *
     * @param type 자원 클래스
     * @return 스캔된 자원 상태
     * @throws ResourceScanException 선언이 잘못된 경우
     */
    private static ScannedResourceState scanOne(Class<?> type) {
        String logicalId = logicalIdOf(type);

        // MVP-2 는 ProviderResource 가 아니면 kind 를 null 로 두고 넘어갔다. 그러면 파이프라인 한참 뒤에서
        // 터진다. 스캔 시점에 던진다 (plan.md §7-D).
        if (!ProviderResource.class.isAssignableFrom(type)) {
            throw new ResourceScanException(
                    type.getName()
                            + " 가 ProviderResource 를 상속하지 않는다. 자원은 프로바이더의 자원 계층 아래에 있어야 한다.");
        }

        ProviderResource instance = instantiate(type.asSubclass(ProviderResource.class));
        Kind kind = instance.kind;
        if (kind == null) {
            throw new ResourceScanException(
                    type.getName() + " 의 kind 가 비어 있다. 프로바이더의 자원 타입을 상속하거나 kind 를 직접 채워야 한다.");
        }

        // TODO(3번 PR): 필드 순회(자식 → 부모)로 config / dependencies / requiredFields 채우기
        //               + @Behavior 매크로 annotation 포착. plan.md §7-B, §7-C, §7-F.
        return new ScannedResourceState(kind, logicalId, Map.of(), List.of(), Set.of(), List.of());
    }

    /**
     * {@code @Resource(name)} 을 읽어 검증한 뒤 돌려준다.
     *
     * <p>logicalId 는 state 파일의 키이자 렌더링에 쓰이는 이름이라 비어 있거나 공백이 섞이면 안 된다.
     *
     * @param type 자원 클래스
     * @return 검증된 logicalId
     * @throws ResourceScanException name 이 비었거나 공백이 섞인 경우
     */
    private static String logicalIdOf(Class<?> type) {
        String name = type.getAnnotation(Resource.class).name();

        if (name.isBlank()) {
            throw new ResourceScanException(
                    type.getName()
                            + " 의 @Resource(name) 이 비어 있다. logicalId 는 state 파일의 키라 비면 안 된다.");
        }
        if (name.chars().anyMatch(Character::isWhitespace)) {
            throw new ResourceScanException(
                    type.getName()
                            + " 의 @Resource(name) \""
                            + name
                            + "\" 에 공백이 섞였다. state 파일의 키와 렌더링이 깨진다.");
        }

        return name;
    }

    /**
     * 인자 없는 생성자로 자원을 인스턴스화한다. 사용자가 initializer block 에 적어 둔 값이 이때 채워진다.
     *
     * @param type 자원 클래스
     * @return 인스턴스
     * @throws ResourceScanException 인스턴스화에 실패한 경우. 원인 예외를 cause 로 붙인다
     */
    private static ProviderResource instantiate(Class<? extends ProviderResource> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new ResourceScanException(type.getName() + " 를 인스턴스화하지 못했다. 인자 없는 생성자가 필요하다.", e);
        }
    }
}
