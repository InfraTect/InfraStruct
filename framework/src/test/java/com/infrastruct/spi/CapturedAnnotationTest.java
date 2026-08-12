package com.infrastruct.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.jupiter.api.Test;

/** {@link CapturedAnnotation} 은 "어노테이션 + 핸들러 클래스" 쌍을 담는 불변 레코드다. */
class CapturedAnnotationTest {

    /** 픽스처: 담을 어노테이션. */
    @Retention(RetentionPolicy.RUNTIME)
    @interface FixtureAnno {}

    /** 픽스처: 어노테이션 인스턴스를 얻기 위한 대상. */
    @FixtureAnno
    static class Holder {}

    /** 픽스처: 담을 핸들러 클래스. */
    static class FixtureHandler implements BehaviorHandler<Annotation> {
        @Override
        public void handle(Annotation annotation, ScannedResourceState state) {}
    }

    @Test
    void holdsAnnoAndHandlerClass() {
        Annotation anno = Holder.class.getAnnotation(FixtureAnno.class);

        CapturedAnnotation captured = new CapturedAnnotation(anno, FixtureHandler.class);

        assertThat(captured.anno()).isSameAs(anno);
        assertThat(captured.handlerClass()).isEqualTo(FixtureHandler.class);
    }
}
