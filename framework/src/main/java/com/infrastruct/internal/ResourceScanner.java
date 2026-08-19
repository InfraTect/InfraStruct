package com.infrastruct.internal;

import com.infrastruct.api.Resource;
import com.infrastruct.spi.Behavior;
import com.infrastruct.spi.Kind;
import com.infrastruct.spi.ProviderResource;
import com.infrastruct.spi.Required;
import com.infrastruct.spi.ScannedResourceState;
import com.infrastruct.spi.ScannedResources;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
 * <p>주의: 같은 패키지에 {@link Comparator} 가 있어 simple name {@code Comparator} 는 그쪽으로 잡힌다. 정렬에는 {@code
 * java.util.Comparator} 를 full qualify 해서 써야 한다.
 */
public final class ResourceScanner {

    /**
     * 자원의 설정값이 아니라 자원을 식별하는 메타 필드. config 에 섞이면 Comparator 가 "종류가 바뀌었다"를 설정 변경으로 오인한다 ({@code
     * plan.md} §7-B).
     */
    private static final Set<String> META_FIELDS = Set.of("kind", "provider");

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
     * <p>필드는 자식부터 부모로 올라가며 읽는다. 참조 필드({@code Class} 값)는 dependencies 로, 나머지 non-null 값은 config 로,
     * {@code @Required} 가 붙은 이름은 requiredFields 로 간다. null 값 필드는 어디에도 넣지 않는다. "없음"은 키 부재로 표현한다
     * ({@code plan.md} §6, §8).
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

        Map<String, Object> config = new LinkedHashMap<>();
        List<String> dependencies = new ArrayList<>();
        Set<String> requiredFields = new LinkedHashSet<>();

        for (Field field : fieldsOf(type)) {
            if (field.isAnnotationPresent(Required.class)) {
                requiredFields.add(field.getName());
            }

            Object value = valueOf(type, field, instance);
            List<String> references = referencesOf(type, field, value);
            if (!references.isEmpty()) {
                dependencies.addAll(references);
            } else if (value != null) {
                config.put(field.getName(), value);
            }
        }

        return new ScannedResourceState(
                kind, logicalId, config, dependencies, requiredFields, captureAnnotations(type));
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

    /**
     * 읽을 필드를 자식부터 부모 순서로 모아 돌려준다.
     *
     * <p>순회는 {@link ProviderResource} <b>직전</b>에서 멈춘다(거기 있는 것은 메타 필드뿐이다). 같은 클래스 안에서는 필드명 사전순으로
     * 정렬한다. {@code getDeclaredFields()} 가 순서를 보장하지 않아, 고정하지 않으면 dependencies 의 원소 순서가 머신마다 달라진다
     * ({@code plan.md} §7-A). 자식과 부모가 같은 이름을 선언했으면(shadowing) 먼저 만난 자식 것이 이긴다 ({@code plan.md}
     * §7-F).
     *
     * @param type 자원 클래스
     * @return 읽을 필드 목록. static, synthetic, 메타 필드는 뺀다
     */
    private static List<Field> fieldsOf(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Class<?> level = type;
                level != ProviderResource.class && level != Object.class;
                level = level.getSuperclass()) {
            Field[] declared = level.getDeclaredFields();
            Arrays.sort(declared, java.util.Comparator.comparing(Field::getName));

            for (Field field : declared) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (META_FIELDS.contains(field.getName())) {
                    continue;
                }
                if (!seen.add(field.getName())) {
                    continue; // 자식이 이미 담은 이름. shadowing 은 자식이 이긴다.
                }
                fields.add(field);
            }
        }

        return fields;
    }

    /**
     * 필드 값을 읽는다.
     *
     * <p>사용자 자원의 필드는 선언 방식상 public 이지만, 아닐 때도 스캔이 멈추지 않게 접근을 연다.
     *
     * @param type 자원 클래스 (에러 메시지용)
     * @param field 읽을 필드
     * @param instance 값을 꺼낼 인스턴스
     * @return 필드 값. 채워지지 않았으면 {@code null}
     * @throws ResourceScanException 값을 읽지 못한 경우. 원인 예외를 cause 로 붙인다
     */
    @SuppressFBWarnings(
            value = "DP_DO_INSIDE_DO_PRIVILEGED",
            justification =
                    "reflection 스캐너의 본질적 동작. SecurityManager 는 JDK 21 에서 deprecated 로 사실상 비활성")
    private static Object valueOf(Class<?> type, Field field, ProviderResource instance) {
        try {
            if (!field.canAccess(instance)) {
                field.setAccessible(true);
            }
            return field.get(instance);
        } catch (IllegalAccessException | InaccessibleObjectException e) {
            throw new ResourceScanException(
                    type.getName() + " 의 필드 " + field.getName() + " 값을 읽지 못했다.", e);
        }
    }

    /**
     * 필드 값에서 자원 참조를 뽑아낸다. dependencies 타입 결정(§7-C)이 바뀌어도 여기와 그 test 만 고치면 되게 격리한 메서드다.
     *
     * <p>{@code Class} 값 하나, 또는 원소가 전부 {@code Class} 인 컬렉션을 참조로 본다. 컬렉션을 원소별로 푸는 것은 RDS 가 subnet 2개
     * 이상을 배열로 요구하기 때문이다 ({@code plan.md} §5). 그 외 값은 참조가 아니므로 빈 목록을 돌려준다.
     *
     * @param type 자원 클래스 (에러 메시지용)
     * @param field 값을 꺼낸 필드 (에러 메시지용)
     * @param value 필드 값
     * @return 참조하는 자원들의 logicalId. 참조가 아니면 빈 목록
     * @throws ResourceScanException 참조 대상에 {@code @Resource} 가 없는 경우
     */
    private static List<String> referencesOf(Class<?> type, Field field, Object value) {
        if (value instanceof Class<?> target) {
            return List.of(referencedLogicalId(type, field, target));
        }
        if (value instanceof Collection<?> elements
                && !elements.isEmpty()
                && elements.stream().allMatch(element -> element instanceof Class)) {
            return elements.stream()
                    .map(element -> referencedLogicalId(type, field, (Class<?>) element))
                    .toList();
        }
        return List.of();
    }

    /**
     * 참조 대상 클래스의 logicalId 를 읽는다.
     *
     * <p>{@code @Resource} 없는 클래스를 가리키는 것은 참조 오타다. 조용히 config 로 흘리면 {@code Class} 객체가 state 파일
     * 직렬화에서 터지므로 스캔 시점에 거부한다.
     *
     * @param type 참조를 들고 있는 자원 클래스 (에러 메시지용)
     * @param field 참조 필드 (에러 메시지용)
     * @param target 참조 대상 클래스
     * @return 참조 대상의 logicalId
     * @throws ResourceScanException 참조 대상에 {@code @Resource} 가 없는 경우
     */
    private static String referencedLogicalId(Class<?> type, Field field, Class<?> target) {
        Resource resource = target.getAnnotation(Resource.class);
        if (resource == null) {
            throw new ResourceScanException(
                    type.getName()
                            + " 의 필드 "
                            + field.getName()
                            + " 가 가리키는 "
                            + target.getName()
                            + " 에 @Resource 가 없다. 참조 대상은 @Resource 로 선언된 자원이어야 한다.");
        }
        return resource.name();
    }

    /**
     * 자원에 붙은 매크로 annotation 을 포착한다.
     *
     * <p>{@link Behavior} 가 달린 annotation 만 담는다. {@code @Resource} 는 {@code @Behavior} 가 없으므로 자연히
     * 빠진다. 결과는 annotation type 이름순으로 정렬한다. {@code getAnnotations()} 의 순서는 보장이 없다 ({@code plan.md}
     * §7-A).
     *
     * @param type 자원 클래스
     * @return 포착된 매크로 annotation 목록
     */
    private static List<CapturedAnnotation> captureAnnotations(Class<?> type) {
        List<CapturedAnnotation> captured = new ArrayList<>();
        for (Annotation annotation : type.getAnnotations()) {
            Behavior behavior = annotation.annotationType().getAnnotation(Behavior.class);
            if (behavior != null) {
                captured.add(new CapturedAnnotation(annotation, behavior.handler()));
            }
        }
        captured.sort(
                java.util.Comparator.comparing(
                        capture -> capture.anno().annotationType().getName()));
        return captured;
    }
}
