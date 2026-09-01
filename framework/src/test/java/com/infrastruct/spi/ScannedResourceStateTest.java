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
        public ScannedResourceState handle(Annotation annotation, ScannedResourceState state) {
            return state;
        }
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

    @Test
    void withConfigEntryCopiesWithOneEntryAddedOrOverwritten() {
        CapturedAnnotation annotation = captured();
        ScannedResourceState origin =
                new ScannedResourceState(
                        TEST_KIND,
                        "ec2.myEc2",
                        Map.of("a", 1),
                        List.of("vpc.myVpc"),
                        Set.of("a"),
                        List.of(annotation));

        ScannedResourceState added = origin.withConfigEntry("b", 2);
        ScannedResourceState overwritten = origin.withConfigEntry("a", 9);

        assertThat(added.config()).containsOnly(Map.entry("a", 1), Map.entry("b", 2));
        assertThat(overwritten.config()).containsOnly(Map.entry("a", 9));
        // 불변이므로 원본은 그대로여야 한다 — 사본을 돌려주는 것이 이 메서드의 존재 이유다.
        assertThat(origin.config()).containsOnly(Map.entry("a", 1));
        // config 외의 필드를 하나라도 빠뜨리면 그 자원의 의존 관계가 조용히 사라진다.
        assertThat(added.kind()).isSameAs(TEST_KIND);
        assertThat(added.logicalId()).isEqualTo("ec2.myEc2");
        assertThat(added.dependencies()).containsExactly("vpc.myVpc");
        assertThat(added.requiredFields()).containsExactly("a");
        assertThat(added.capturedAnnotations()).containsExactly(annotation);
    }
}
