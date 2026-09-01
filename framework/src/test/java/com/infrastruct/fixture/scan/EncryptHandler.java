package com.infrastruct.fixture.scan;

import com.infrastruct.spi.BehaviorHandler;
import com.infrastruct.spi.ScannedResourceState;

/**
 * {@link Encrypted} 를 처리하는 fixture 핸들러.
 *
 * <p>스캐너는 핸들러를 <b>호출하지 않고</b> 클래스만 담아 넘긴다. 본문은 비워 둔다.
 */
public final class EncryptHandler implements BehaviorHandler<Encrypted> {

    @Override
    public void handle(Encrypted annotation, ScannedResourceState state) {
        // 스캔 test 는 포착까지만 본다.
    }
}
