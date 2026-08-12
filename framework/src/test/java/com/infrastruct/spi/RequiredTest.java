package com.infrastruct.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;

/** {@link Required} 는 자원의 필수 필드를 표시하는 RUNTIME/FIELD 마커다. */
class RequiredTest {

    /** 픽스처: 프로바이더 자원 템플릿의 필수 필드를 흉내 낸다. */
    static class SampleResource {
        @Required public String vpc;
    }

    @Test
    void isRuntimeFieldMarker() throws NoSuchFieldException {
        Required anno = SampleResource.class.getField("vpc").getAnnotation(Required.class);
        Target target = Required.class.getAnnotation(Target.class);

        assertThat(anno).isNotNull(); // RUNTIME 리텐션
        assertThat(target).isNotNull();
        assertThat(target.value()).contains(ElementType.FIELD); // 필드에 붙음
    }
}
