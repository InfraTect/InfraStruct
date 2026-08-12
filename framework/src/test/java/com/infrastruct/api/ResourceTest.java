package com.infrastruct.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;

/** {@link Resource} 어노테이션의 계약(RUNTIME + TYPE + name)을 검증한다. */
class ResourceTest {

    /** 픽스처: 사용자가 정의하는 자원 클래스를 흉내 낸다. */
    @Resource(name = "myEc2")
    static class SampleResource {}

    @Test
    void isRuntimeTypeWithName() {
        Resource anno = SampleResource.class.getAnnotation(Resource.class);
        Target target = Resource.class.getAnnotation(Target.class);

        assertThat(anno).isNotNull(); // RUNTIME 리텐션
        assertThat(anno.name()).isEqualTo("myEc2"); // 속성값 왕복
        assertThat(target).isNotNull();
        assertThat(target.value()).contains(ElementType.TYPE); // 클래스에 붙음
    }
}
