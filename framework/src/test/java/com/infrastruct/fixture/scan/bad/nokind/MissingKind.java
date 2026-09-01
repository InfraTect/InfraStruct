package com.infrastruct.fixture.scan.bad.nokind;

import com.infrastruct.api.Resource;
import com.infrastruct.fixture.scan.ScanResource;

/**
 * 깨진 자원: {@code ScanResource} 는 상속하지만 kind 를 채우지 않는다.
 *
 * <p>MVP-2 는 이걸 null 로 두고 넘어갔다. 그러면 파이프라인 한참 뒤에서 터진다 ({@code plan.md} §7-D).
 */
@Resource(name = "noKind")
public class MissingKind extends ScanResource {}
