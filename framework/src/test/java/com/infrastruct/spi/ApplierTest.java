package com.infrastruct.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link Applier} 는 순서 있는 변경셋을 실제 상태에 적용하고 새 {@link CurrentResources} 를 돌려주는 계약이다. */
class ApplierTest {

    /** 픽스처: 넘어온 값을 기록하고 미리 정해 둔 결과를 돌려주는 구현체. */
    static class RecordingApplier implements Applier {
        OrderedResourceChangeSet receivedPlan;
        CurrentResources receivedCurrent;
        final CurrentResources result;

        RecordingApplier(CurrentResources result) {
            this.result = result;
        }

        @Override
        public CurrentResources apply(OrderedResourceChangeSet plan, CurrentResources current) {
            this.receivedPlan = plan;
            this.receivedCurrent = current;
            return result;
        }
    }

    @Test
    void appliesPlanAndReturnsNewState() {
        CurrentResources result = new CurrentResources(List.of());
        RecordingApplier applier = new RecordingApplier(result);
        OrderedResourceChangeSet plan = new OrderedResourceChangeSet(List.of());
        CurrentResources current = new CurrentResources(List.of());

        CurrentResources returned = applier.apply(plan, current);

        assertThat(applier.receivedPlan).isSameAs(plan);
        assertThat(applier.receivedCurrent).isSameAs(current);
        assertThat(returned).isSameAs(result);
    }
}
