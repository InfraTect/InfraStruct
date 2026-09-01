package com.infrastruct.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;

/** {@link Behavior} 어노테이션의 계약(RUNTIME + ANNOTATION_TYPE + handler)을 검증한다. */
class BehaviorTest {

    /** 픽스처: 아무 핸들러 구현체. */
    static class FixtureHandler implements BehaviorHandler<Annotation> {
        @Override
        public ScannedResourceState handle(Annotation annotation, ScannedResourceState state) {
            return state;
        }
    }

    /** 픽스처: @Behavior 가 붙은 매크로 어노테이션 선언. */
    @Behavior(handler = FixtureHandler.class)
    @interface FixtureMacro {}

    @Test
    void isRuntimeAnnotationTypeWithHandler() {
        Behavior anno = FixtureMacro.class.getAnnotation(Behavior.class);
        Target target = Behavior.class.getAnnotation(Target.class);

        assertThat(anno).isNotNull(); // RUNTIME 리텐션
        assertThat(anno.handler()).isEqualTo(FixtureHandler.class); // 속성값 왕복
        assertThat(target).isNotNull();
        assertThat(target.value()).contains(ElementType.ANNOTATION_TYPE); // 어노테이션 선언에 붙음
    }
}
