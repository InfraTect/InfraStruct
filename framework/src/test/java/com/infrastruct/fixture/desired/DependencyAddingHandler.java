package com.infrastruct.fixture.desired;

import com.infrastruct.spi.BehaviorHandler;
import com.infrastruct.spi.ScannedResourceState;
import java.util.ArrayList;
import java.util.List;

/**
 * 의존 자원을 덧붙이는 핸들러 픽스처.
 *
 * <p>이것은 계약 위반이 <b>아니다</b> — 매크로가 의존 관계를 더하는 것은 정상적인 일이다. 잠기는 것은 식별자(kind, logicalId)뿐이라는 것을 반증
 * 가능하게 만든다.
 */
public final class DependencyAddingHandler implements BehaviorHandler<Encrypted> {

    @Override
    public ScannedResourceState handle(Encrypted annotation, ScannedResourceState state) {
        List<String> dependencies = new ArrayList<>(state.dependencies());
        dependencies.add("vpc.added");
        return new ScannedResourceState(
                state.kind(),
                state.logicalId(),
                state.config(),
                dependencies,
                state.requiredFields(),
                state.capturedAnnotations());
    }
}
