package com.infrastruct.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import org.junit.jupiter.api.Test;

/** {@link RegisterProvider} 어노테이션의 계약(RUNTIME + TYPE + 속성 3개)을 검증한다. */
class RegisterProviderTest {

    /** 픽스처: validator 자리에 넣을 아무 클래스(상한이 Class<?> 라 무엇이든 됨). */
    static class DummyValidator {}

    /** 픽스처: applier 자리에 넣을 Applier 구현 (좁아진 상한 {@code Class<? extends Applier>} 를 만족한다). */
    static class DummyApplier implements Applier {
        @Override
        public CurrentResources apply(OrderedResourceChangeSet plan, CurrentResources current) {
            return current;
        }
    }

    /** 픽스처: {@code @RegisterProvider(...) class Aws extends Provider {}} 를 흉내 낸다. */
    @RegisterProvider(
            providerId = "aws",
            validator = DummyValidator.class,
            applier = DummyApplier.class)
    static class TestProvider extends Provider {}

    @Test
    void isRuntimeTypeWithAttributes() {
        RegisterProvider anno = TestProvider.class.getAnnotation(RegisterProvider.class);
        Target target = RegisterProvider.class.getAnnotation(Target.class);

        assertThat(anno).isNotNull(); // RUNTIME 리텐션
        assertThat(anno.providerId()).isEqualTo("aws");
        assertThat(anno.validator()).isEqualTo(DummyValidator.class);
        assertThat(anno.applier()).isEqualTo(DummyApplier.class);
        assertThat(target).isNotNull();
        assertThat(target.value()).contains(ElementType.TYPE); // 클래스에 붙음
    }

    @Test
    void applierBoundIsNarrowedToApplier() throws NoSuchMethodException {
        Type ret = RegisterProvider.class.getMethod("applier").getGenericReturnType();
        Type arg = ((ParameterizedType) ret).getActualTypeArguments()[0]; // ? extends Applier
        Type bound = ((WildcardType) arg).getUpperBounds()[0];

        assertThat(bound).isEqualTo(Applier.class);
    }
}
