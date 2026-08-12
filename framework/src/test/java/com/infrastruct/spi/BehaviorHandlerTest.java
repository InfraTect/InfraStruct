package com.infrastruct.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.jupiter.api.Test;

/** {@link BehaviorHandler} 는 매크로 어노테이션의 효과를 자원 상태에 반영하는 제네릭 핸들러 계약이다. */
class BehaviorHandlerTest {

    /** 픽스처: 처리 대상이 될 매크로 어노테이션. */
    @Retention(RetentionPolicy.RUNTIME)
    @interface FixtureAnno {}

    /** 픽스처: 어노테이션을 붙여 실제 인스턴스를 얻기 위한 대상. */
    @FixtureAnno
    static class Holder {}

    /** 픽스처: 넘어온 값을 기록하는 핸들러 구현체. */
    static class RecordingHandler implements BehaviorHandler<FixtureAnno> {
        FixtureAnno received;
        ScannedResourceState receivedState;

        @Override
        public void handle(FixtureAnno annotation, ScannedResourceState state) {
            this.received = annotation;
            this.receivedState = state;
        }
    }

    /** 픽스처: 넘어온 상태를 실제로 고치는 핸들러 구현체. */
    static class MutatingHandler implements BehaviorHandler<FixtureAnno> {
        @Override
        public void handle(FixtureAnno annotation, ScannedResourceState state) {
            state.getConfig().put("allowSsh", true);
        }
    }

    @Test
    void implementationHandlesAnnotationAndState() {
        RecordingHandler handler = new RecordingHandler();
        FixtureAnno anno = Holder.class.getAnnotation(FixtureAnno.class);
        ScannedResourceState state = new ScannedResourceState();

        handler.handle(anno, state);

        assertThat(handler.received).isSameAs(anno);
        assertThat(handler.receivedState).isSameAs(state);
    }

    @Test
    void handlerMutatesGivenState() {
        MutatingHandler handler = new MutatingHandler();
        FixtureAnno anno = Holder.class.getAnnotation(FixtureAnno.class);
        ScannedResourceState state = new ScannedResourceState();

        handler.handle(anno, state);

        assertThat(state.getConfig()).containsEntry("allowSsh", true);
    }
}
