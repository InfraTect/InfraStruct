package com.infrastruct.internal;

import static java.util.stream.Collectors.joining;

import com.infrastruct.spi.Applier;
import com.infrastruct.spi.Provider;
import com.infrastruct.spi.RegisterProvider;
import com.infrastruct.spi.Validator;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * {@code @InfraStructApplication(provider = "...")} 의 문자열 하나를, 그 프로바이더가 등록한 모듈로 바꿔 준다.
 *
 * <p>classpath 에서 {@link RegisterProvider} 가 붙은 토큰을 전수 스캔해 {@code providerId} 가 일치하는 하나를 고르고, 그 토큰이
 * 등록한 클래스들을 보관한다. 스캔·선택은 생성자에서, 객체화는 접근자에서 한다.
 */
public final class ModuleRegistry {

    /** 고른 프로바이더 토큰. 다이어그램대로 필드는 {@code Class} 를 들고, 접근자가 인스턴스를 돌려준다. */
    private final Class<? extends Provider> provider;

    private final Class<? extends Validator> validator;

    private final Class<? extends Applier> applier;

    /**
     * 주어진 식별자에 해당하는 프로바이더 토큰을 classpath 에서 찾아, 그 토큰이 등록한 세 클래스를 보관한다.
     *
     * @param providerId {@code @InfraStructApplication(provider=...)} 로 선언된 프로바이더 식별자
     * @throws ModuleRegistryException 식별자가 비었거나, 토큰을 찾지 못했거나, 등록이 잘못된 경우
     */
    public ModuleRegistry(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            throw new ModuleRegistryException(
                    "provider 식별자가 비어 있습니다. 메인 클래스의 @InfraStructApplication(provider = \"...\") 에"
                            + " 사용할 프로바이더를 선언하세요 (예: provider = \"aws\").");
        }
        this.provider = findToken(providerId);
        RegisterProvider registration = provider.getAnnotation(RegisterProvider.class);
        this.validator = narrow(registration.validator(), Validator.class);
        this.applier = narrow(registration.applier(), Applier.class);
    }

    /**
     * 등록된 검증기 구현의 새 인스턴스.
     *
     * <p>캐싱하지 않는다 — 부를 때마다 새로 만든다. 인스턴스를 필드에 담아 두면 실행 간·테스트 간 상태가 새어 나간다.
     *
     * @return 등록된 {@link Validator} 구현의 새 인스턴스
     */
    public Validator validator() {
        return newInstance(validator);
    }

    /**
     * 등록된 적용기 구현의 새 인스턴스.
     *
     * @return 등록된 {@link Applier} 구현의 새 인스턴스
     */
    public Applier applier() {
        return newInstance(applier);
    }

    /**
     * 스캔으로 고른 토큰 클래스.
     *
     * <p>선택 결과를 테스트가 읽도록 열어 둔 package-private 접근자다.
     *
     * @return 고른 프로바이더 토큰 클래스
     */
    Class<? extends Provider> providerToken() {
        return provider;
    }

    /**
     * classpath 전체에서 {@code providerId} 가 일치하는 토큰을 찾는다.
     *
     * <p>찾지 못하면 <b>발견된 id 목록</b>을 함께 담아 던진다. 오타는 "없다"는 말만으로는 고칠 수 없고, 무엇이 있었는지 보여줘야 고칠 수 있다. 목록은 중복
     * 제거·사전순이라 스캔 순서에 따라 메시지가 흔들리지 않는다.
     *
     * @param providerId 찾을 프로바이더 식별자
     * @return 그 식별자를 선언한 단 하나의 토큰 클래스
     * @throws ModuleRegistryException 토큰이 없거나, 둘 이상이거나, {@link Provider} 를 상속하지 않은 경우
     */
    private static Class<? extends Provider> findToken(String providerId) {
        SortedMap<String, List<Class<?>>> tokensById = scanTokensById();
        List<Class<?>> matched = tokensById.getOrDefault(providerId, List.of());
        if (matched.size() > 1) {
            throw new ModuleRegistryException(
                    "provider \""
                            + providerId
                            + "\" 를 선언한 토큰이 "
                            + matched.size()
                            + " 개입니다: ["
                            + matched.stream().map(Class::getName).sorted().collect(joining(", "))
                            + "]. 하나만 남기거나 서로 다른 providerId 를 주세요.");
        }
        if (matched.isEmpty()) {
            throw new ModuleRegistryException(
                    "provider \""
                            + providerId
                            + "\" 로 등록된 프로바이더를 classpath 에서 찾지 못했습니다. 발견된 provider: ["
                            + String.join(", ", tokensById.keySet())
                            + "]. @InfraStructApplication 의 provider 값 또는 프로바이더 의존성을 확인하세요.");
        }
        Class<?> token = matched.get(0);
        try {
            return token.asSubclass(Provider.class);
        } catch (ClassCastException e) {
            throw new ModuleRegistryException(
                    token.getName()
                            + " 에 @RegisterProvider 가 붙어 있지만 "
                            + Provider.class.getName()
                            + " 를 상속하지 않았습니다. 프로바이더 토큰은 그 클래스를 상속해야 합니다.",
                    e);
        }
    }

    /**
     * classpath 의 {@link RegisterProvider} 토큰을 전부 모아 providerId 로 묶는다.
     *
     * <p>키가 사전순({@link SortedMap})이라 진단 메시지의 id 목록이 스캔 순서에 흔들리지 않는다. 중복 선언된 id 는 한 키에 값 여러 개로 모인다 —
     * 그래서 목록에는 한 번만 나오고, 충돌은 그 키를 고를 때 드러난다.
     *
     * @return providerId 별로 묶인 토큰 클래스들 (키는 사전순)
     */
    private static SortedMap<String, List<Class<?>>> scanTokensById() {
        SortedMap<String, List<Class<?>>> tokensById = new TreeMap<>();
        try (ScanResult scan = new ClassGraph().enableClassInfo().enableAnnotationInfo().scan()) {
            for (Class<?> token :
                    scan.getClassesWithAnnotation(RegisterProvider.class).loadClasses()) {
                RegisterProvider registration = token.getAnnotation(RegisterProvider.class);
                if (registration == null) {
                    continue;
                }
                tokensById
                        .computeIfAbsent(registration.providerId(), id -> new ArrayList<>())
                        .add(token);
            }
        }
        return tokensById;
    }

    /**
     * 토큰이 등록한 구현 클래스가 요구 타입을 만족하는지 확인하고 좁힌다.
     *
     * <p>Java 소스로는 어길 수 없다 — 어노테이션 멤버의 상한이 컴파일을 막는다. 하지만 어노테이션의 제네릭은 class 파일에서 지워지므로, 상한이 없던 옛 코어로
     * 컴파일한 프로바이더 jar 는 런타임에 아무 {@code Class} 나 돌려줄 수 있다. 그때 {@code ClassCastException} 대신 고칠 곳을 알려
     * 주는 메시지가 나가게 한다.
     *
     * @param <T> 요구 타입
     * @param declared 토큰이 등록한 클래스
     * @param required 그 자리가 요구하는 타입 ({@link Validator} 또는 {@link Applier})
     * @return {@code required} 로 좁힌 클래스
     * @throws ModuleRegistryException 등록된 클래스가 {@code required} 의 하위 타입이 아닌 경우
     */
    private static <T> Class<? extends T> narrow(Class<?> declared, Class<T> required) {
        if (!required.isAssignableFrom(declared)) {
            throw new ModuleRegistryException(
                    declared.getName()
                            + " 가 등록됐지만 "
                            + required.getName()
                            + " 의 하위 타입이 아닙니다. @RegisterProvider 에 등록한 클래스를 확인하세요.");
        }
        return declared.asSubclass(required);
    }

    /**
     * public 무인자 생성자로 구현체를 만든다.
     *
     * <p>{@code getConstructor()} 는 public 생성자만 찾는다 — 프레임워크가 만드는 객체는 public 계약이라는 뜻이고, {@code
     * setAccessible} 로 뚫지 않는다.
     *
     * @param <T> 만들 객체의 타입
     * @param impl 만들 구현 클래스
     * @return 새로 만든 인스턴스
     * @throws ModuleRegistryException 인스턴스화할 수 없거나 생성자가 예외를 던진 경우
     */
    private static <T> T newInstance(Class<T> impl) {
        try {
            return impl.getConstructor().newInstance();
        } catch (InvocationTargetException e) {
            // reflection 래퍼를 벗겨 진짜 원인을 붙인다. 래퍼가 cause 로 남으면 사용자가 원인을 한 겹 더 파야 한다.
            throw new ModuleRegistryException(
                    impl.getName() + " 의 생성자가 예외를 던졌습니다.", e.getTargetException());
        } catch (ReflectiveOperationException e) {
            throw new ModuleRegistryException(
                    impl.getName() + " 를 만들 수 없습니다. 구현체는 public 무인자 생성자를 가진 구체 클래스여야 합니다.", e);
        }
    }
}
