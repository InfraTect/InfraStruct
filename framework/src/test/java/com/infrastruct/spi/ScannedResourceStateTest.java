package com.infrastruct.spi;

import static org.assertj.core.api.Assertions.assertThat;

import com.infrastruct.internal.CapturedAnnotation;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@link ScannedResourceState} 는 스캔 결과에 아직 소비되지 않은 매크로 어노테이션을 얹어 들고 있다. */
class ScannedResourceStateTest {

    private static final Kind TEST_KIND = () -> "test-kind";

    /** 픽스처: 자원에 붙일 매크로 어노테이션. */
    @Retention(RetentionPolicy.RUNTIME)
    @interface FixtureAnno {}

    /** 픽스처: 어노테이션 인스턴스를 얻기 위한 대상. */
    @FixtureAnno
    static class Holder {}

    /** 픽스처: 담을 핸들러 클래스. */
    static class FixtureHandler implements BehaviorHandler<Annotation> {
        @Override
        public void handle(Annotation annotation, Object state) {}
    }

    private static CapturedAnnotation captured() {
        return new CapturedAnnotation(
                Holder.class.getAnnotation(FixtureAnno.class), FixtureHandler.class);
    }

    @Test
    void holdsCapturedAnnotationsOnTopOfCommonFields() {
        CapturedAnnotation annotation = captured();

        ScannedResourceState state =
                new ScannedResourceState(
                        TEST_KIND,
                        "ec2.myEc2",
                        Map.of("instanceType", "t3.micro"),
                        List.of("vpc.myVpc"),
                        Set.of("instanceType"),
                        List.of(annotation));

        assertThat(state.logicalId()).isEqualTo("ec2.myEc2");
        assertThat(state.config()).containsExactly(Map.entry("instanceType", "t3.micro"));
        assertThat(state.dependencies()).containsExactly("vpc.myVpc");
        assertThat(state.capturedAnnotations()).containsExactly(annotation);
    }
}
