package com.infrastruct.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.infrastruct.fixture.provider.AbstractFixtureValidator;
import com.infrastruct.fixture.provider.AlphaApplier;
import com.infrastruct.fixture.provider.AlphaProvider;
import com.infrastruct.fixture.provider.AlphaValidator;
import com.infrastruct.fixture.provider.BetaProvider;
import com.infrastruct.fixture.provider.BetaValidator;
import com.infrastruct.fixture.provider.NoCtorApplier;
import com.infrastruct.fixture.provider.PrivateCtorValidator;
import com.infrastruct.fixture.provider.RogueToken;
import com.infrastruct.fixture.provider.ThrowingValidator;
import com.infrastruct.fixture.provider.TwinOneProvider;
import com.infrastruct.fixture.provider.TwinTwoProvider;
import com.infrastruct.fixture.provider.UnregisteredProvider;
import com.infrastruct.spi.Applier;
import com.infrastruct.spi.Provider;
import org.junit.jupiter.api.Test;

class ModuleRegistryTest {

    @Test
    void moduleRegistryExceptionCarriesMessage() {
        assertThatThrownBy(
                        () -> {
                            throw new ModuleRegistryException("com.example.Aws 를 찾을 수 없다");
                        })
                .isInstanceOf(RuntimeException.class)
                .hasMessage("com.example.Aws 를 찾을 수 없다");
    }

    @Test
    void moduleRegistryExceptionKeepsCause() {
        NoSuchMethodException cause = new NoSuchMethodException("<init>");

        assertThatThrownBy(
                        () -> {
                            throw new ModuleRegistryException(
                                    "com.example.AwsValidator 인스턴스화 실패", cause);
                        })
                .isInstanceOf(ModuleRegistryException.class)
                .hasCause(cause);
    }

    /**
     * alpha·beta 두 토큰이 같은 classpath 에 있을 때, 요청한 id 쪽을 고른다. 두 id 를 모두 확인해야 "찾은 첫 토큰을 그냥 쓴다"는 구현을 반증할
     * 수 있다.
     */
    @Test
    void picksTokenWhoseProviderIdMatches() {
        assertThat(new ModuleRegistry("alpha").providerToken()).isEqualTo(AlphaProvider.class);
        assertThat(new ModuleRegistry("beta").providerToken()).isEqualTo(BetaProvider.class);
    }

    @Test
    void createsInstancesOfRegisteredImplementations() {
        ModuleRegistry alpha = new ModuleRegistry("alpha");

        assertThat(alpha.validator()).isExactlyInstanceOf(AlphaValidator.class);
        assertThat(alpha.applier()).isExactlyInstanceOf(AlphaApplier.class);
        // 토큰과 무관하게 한 구현만 돌려주는 구현을 반증한다.
        assertThat(new ModuleRegistry("beta").validator()).isExactlyInstanceOf(BetaValidator.class);
    }

    /** 캐싱하지 않는다는 계약을 고정한다 — 부를 때마다 새 인스턴스다. */
    @Test
    void createsNewInstanceOnEveryCall() {
        ModuleRegistry alpha = new ModuleRegistry("alpha");

        assertThat(alpha.validator()).isNotSameAs(alpha.validator());
        assertThat(alpha.applier()).isNotSameAs(alpha.applier());
    }

    /**
     * {@code @InfraStructApplication(provider = "")} 은 사용자가 실제로 낼 수 있는 오타다. 가드가 없으면 null 은 NPE 로,
     * 공백은 "발견된 id 목록"만 뱉는 메시지로 끝나 어디가 틀렸는지 알 수 없다.
     */
    @Test
    void rejectsBlankProviderId() {
        assertThatThrownBy(() -> new ModuleRegistry(null))
                .isInstanceOf(ModuleRegistryException.class);
        assertThatThrownBy(() -> new ModuleRegistry(""))
                .isInstanceOf(ModuleRegistryException.class)
                .hasMessageContaining("@InfraStructApplication");
        assertThatThrownBy(() -> new ModuleRegistry("   "))
                .isInstanceOf(ModuleRegistryException.class)
                .hasMessageContaining("@InfraStructApplication");
    }

    /** 오타 진단용 — 찾던 id 와 classpath 에서 실제로 발견된 id 들을 함께 보여준다. */
    @Test
    void throwsWithFoundIdsWhenNoTokenMatches() {
        Throwable thrown = catchThrowable(() -> new ModuleRegistry("no-such-provider"));

        assertThat(thrown).isInstanceOf(ModuleRegistryException.class);
        assertThat(thrown.getMessage())
                .contains("no-such-provider")
                .contains("alpha")
                .contains("beta")
                // 스캔 기준은 상속이 아니라 어노테이션이다.
                .doesNotContain(UnregisteredProvider.class.getSimpleName())
                // 중복 선언된 id 라도 진단 목록에는 한 번만 나온다.
                .containsOnlyOnce("twin");
    }

    /** 하나만 알려 주면 사용자가 나머지 하나를 직접 찾아야 한다 — 충돌한 토큰을 모두 담는다. */
    @Test
    void throwsWhenTwoTokensShareProviderId() {
        assertThatThrownBy(() -> new ModuleRegistry("twin"))
                .isInstanceOf(ModuleRegistryException.class)
                .hasMessageContaining("twin")
                .hasMessageContaining(TwinOneProvider.class.getName())
                .hasMessageContaining(TwinTwoProvider.class.getName());
    }

    /** 검증은 <b>요청한 id 에 대해서만</b> 한다 — 남의 프로바이더가 깨져 있다고 내 실행이 막히지 않는다. */
    @Test
    void ignoresDuplicatesOfOtherProviderIds() {
        assertThat(new ModuleRegistry("alpha").providerToken()).isEqualTo(AlphaProvider.class);
    }

    /** 무엇을 상속해야 하는지까지 알려 준다 — 토큰 FQCN 과 상속해야 할 {@code Provider} FQCN 을 모두 담는다. */
    @Test
    void throwsWhenTokenDoesNotExtendProvider() {
        assertThatThrownBy(() -> new ModuleRegistry("rogue"))
                .isInstanceOf(ModuleRegistryException.class)
                .hasMessageContaining(RogueToken.class.getName())
                .hasMessageContaining(Provider.class.getName());
    }

    /** 무엇이 잘못됐는지(그 클래스) 와 무엇을 요구하는지(public 무인자 생성자)를 함께 담는다. */
    @Test
    void throwsWhenImplementationCannotBeInstantiated() {
        assertThatThrownBy(() -> new ModuleRegistry("abstract-validator").validator())
                .isInstanceOf(ModuleRegistryException.class)
                .hasMessageContaining(AbstractFixtureValidator.class.getName())
                .hasMessageContaining("public 무인자 생성자");
        // applier 경로도 같게 처리된다.
        assertThatThrownBy(() -> new ModuleRegistry("no-ctor").applier())
                .isInstanceOf(ModuleRegistryException.class)
                .hasMessageContaining(NoCtorApplier.class.getName())
                .hasMessageContaining("public 무인자 생성자");
        // setAccessible 로 뚫지 않는다.
        assertThatThrownBy(() -> new ModuleRegistry("private-ctor").validator())
                .isInstanceOf(ModuleRegistryException.class)
                .hasMessageContaining(PrivateCtorValidator.class.getName())
                .hasMessageContaining("public 무인자 생성자");
        assertThatThrownBy(() -> new ModuleRegistry("interface-applier").applier())
                .isInstanceOf(ModuleRegistryException.class)
                .hasMessageContaining(Applier.class.getName())
                .hasMessageContaining("public 무인자 생성자");
    }

    /** 스캔·선택은 생성자, 객체화는 접근자라는 분담 — 깨진 구현체라도 생성자는 통과한다. */
    @Test
    void doesNotInstantiateInConstructor() {
        assertThatCode(() -> new ModuleRegistry("abstract-validator")).doesNotThrowAnyException();
    }

    /** reflection 래퍼({@code InvocationTargetException})를 벗겨 진짜 원인을 cause 로 붙인다. */
    @Test
    void unwrapsExceptionThrownByImplementationConstructor() {
        Throwable thrown = catchThrowable(() -> new ModuleRegistry("throwing").validator());

        assertThat(thrown)
                .isInstanceOf(ModuleRegistryException.class)
                .hasMessageContaining(ThrowingValidator.class.getName());
        assertThat(thrown.getCause()).isInstanceOf(IllegalStateException.class).hasMessage("boom");
    }

    @Test
    void matchesProviderIdExactly() {
        assertThatThrownBy(() -> new ModuleRegistry("ALPHA"))
                .isInstanceOf(ModuleRegistryException.class);
    }
}
