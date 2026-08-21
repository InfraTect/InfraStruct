package com.infrastruct.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infrastruct.fixture.provider.AlphaApplier;
import com.infrastruct.fixture.provider.AlphaValidator;
import com.infrastruct.internal.ModuleRegistryException;
import org.junit.jupiter.api.Test;

/**
 * {@link InfraStruct} 진입 클래스의 계약을 검증한다.
 *
 * <p>이제 생성자는 {@code ModuleRegistry} 를 태워 실제로 모듈을 주입한다. 파이프라인 본문({@link InfraStruct#run()})은 아직 비어
 * 있어 "호출해도 예외 없이 반환한다" 수준만 확인한다.
 */
class InfraStructTest {

    /** 픽스처: 사용자의 메인 클래스를 흉내 낸다. */
    @InfraStructApplication(provider = "alpha")
    static class AlphaApp {}

    /** 픽스처: provider 이름을 잘못 적은 메인 클래스. */
    @InfraStructApplication(provider = "no-such-provider")
    static class BrokenApp {}

    /** 픽스처: 어노테이션을 붙이는 것을 잊은 평범한 클래스. */
    static class PlainClass {}

    @Test
    void injectsModulesOfDeclaredProvider() {
        InfraStruct app = new InfraStruct("alpha");

        assertThat(app.validator()).isExactlyInstanceOf(AlphaValidator.class);
        assertThat(app.applier()).isExactlyInstanceOf(AlphaApplier.class);
    }

    /** 파이프라인을 돌기 전 <b>생성자에서</b> 즉시 실패한다. */
    @Test
    void failsInConstructorWhenProviderIsUnknown() {
        assertThatThrownBy(() -> new InfraStruct("no-such-provider"))
                .isInstanceOf(ModuleRegistryException.class);
    }

    @Test
    void instanceRunDoesNotThrow() {
        InfraStruct app = new InfraStruct("alpha");

        assertThatCode(app::run).doesNotThrowAnyException();
    }

    @Test
    void staticRunDoesNotThrow() {
        assertThatCode(() -> InfraStruct.run(AlphaApp.class)).doesNotThrowAnyException();
    }

    /**
     * {@code run(Class)} 는 만든 인스턴스를 밖으로 내보내지 않는다. 그래서 "정말 넘겼는가"는 실패 경로로 관찰한다 — 어노테이션에 적은 값이 그대로 담긴
     * 예외가 올라오면 registry 까지 흘러갔다는 뜻이다.
     */
    @Test
    void staticRunPassesAnnotationValueToRegistry() {
        assertThatThrownBy(() -> InfraStruct.run(BrokenApp.class))
                .isInstanceOf(ModuleRegistryException.class)
                .hasMessageContaining("no-such-provider");
    }

    /** 어노테이션을 빠뜨린 것은 흔한 실수다 — NPE 대신 어디에 무엇을 붙여야 하는지 알려 준다. */
    @Test
    void throwsWhenMainClassHasNoAnnotation() {
        assertThatThrownBy(() -> InfraStruct.run(PlainClass.class))
                .isInstanceOf(ModuleRegistryException.class)
                .hasMessageContaining(PlainClass.class.getName())
                .hasMessageContaining("@InfraStructApplication");
    }
}
