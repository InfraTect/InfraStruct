package com.infrastruct.fixture.scan;

import com.infrastruct.spi.BehaviorHandler;
import com.infrastruct.spi.ScannedResourceState;

/**
 * {@link Tagged} 를 처리하는 fixture 핸들러.
 *
 * <p>스캐너는 핸들러를 <b>호출하지 않고</b> 클래스만 담아 넘긴다 (소비는 DesiredStateCreator 의 몫). 그래서 본문은 비워 둔다.
 */
public final class TagHandler implements BehaviorHandler<Tagged> {

    @Override
    public void handle(Tagged annotation, ScannedResourceState state) {
        // 스캔 test 는 포착까지만 본다. 소비는 이 fixture 의 관심사가 아니다.
    }
}
