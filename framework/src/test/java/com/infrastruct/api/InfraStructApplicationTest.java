package com.infrastruct.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;

/** {@link InfraStructApplication} 어노테이션의 계약을 검증한다. */
class InfraStructApplicationTest {

    /** 픽스처: 어노테이션을 실제로 붙여 리플렉션으로 읽어볼 대상. */
    @InfraStructApplication(provider = "aws")
    static class SampleApp {}

    @Test
    void retainedAtRuntime() {
        // RUNTIME 리텐션이 아니면 컴파일 후 사라져 이 조회 결과가 null 이 된다.
        InfraStructApplication anno = SampleApp.class.getAnnotation(InfraStructApplication.class);

        assertThat(anno).isNotNull();
    }

    @Test
    void targetsType() {
        // 클래스에만 붙는 어노테이션이므로 @Target 에 TYPE 이 있어야 한다.
        Target target = InfraStructApplication.class.getAnnotation(Target.class);

        assertThat(target).isNotNull();
        assertThat(target.value()).contains(ElementType.TYPE);
    }

    @Test
    void providerReturnsDeclaredValue() {
        InfraStructApplication anno = SampleApp.class.getAnnotation(InfraStructApplication.class);

        assertThat(anno.provider()).isEqualTo("aws");
    }

    @Test
    void providerHasNoDefault() throws NoSuchMethodException {
        // 기본값이 없어야 provider 를 생략했을 때 컴파일 에러가 난다(= 필수 속성).
        Object def = InfraStructApplication.class.getDeclaredMethod("provider").getDefaultValue();

        assertThat(def).isNull();
    }
}
