package com.infrastruct.spi;

import java.util.List;

/**
 * 마지막으로 apply 된 실제 상태 전체. CurrentStateStore 가 JSON 으로 저장/복원하고, Applier 가 적용 후 새로 만들어 반환한다.
 *
 * <p>record 인 이유는 {@link ScannedResources} 참조.
 *
 * @param resources 현재 자원 상태 목록
 */
public record CurrentResources(List<CurrentResourceState> resources) {

    /** 넘어온 목록을 불변으로 복사한다. 근거는 {@link ScannedResources#ScannedResources(List)}. */
    public CurrentResources {
        resources = resources == null ? List.of() : List.copyOf(resources);
    }
}
