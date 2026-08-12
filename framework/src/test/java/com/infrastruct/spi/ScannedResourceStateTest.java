package com.infrastruct.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.jupiter.api.Test;

/** {@link ScannedResourceState} 는 부모 계약에 더해 아직 소비되지 않은 매크로 어노테이션을 들고 있다. */
class ScannedResourceStateTest {

    /** 픽스처: 프로바이더의 자원 종류. */
    enum TestKind implements Kind {
        EC2;

        @Override
        public String value() {
            return name();
        }
    }

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
    void capturedAnnotationsStartEmptyAndAreMutable() {
        ScannedResourceState state = new ScannedResourceState(TestKind.EC2, "myEc2");
        Annotation anno = Holder.class.getAnnotation(FixtureAnno.class);
        CapturedAnnotation captured = new CapturedAnnotation(anno, FixtureHandler.class);

        assertThat(state.getCapturedAnnotations()).isEmpty();
        state.getCapturedAnnotations().add(captured);

        assertThat(state.getCapturedAnnotations()).containsExactly(captured);
        assertThat(state.getKind()).isEqualTo(TestKind.EC2);
        assertThat(state.getLogicalId()).isEqualTo("myEc2");
    }
}
