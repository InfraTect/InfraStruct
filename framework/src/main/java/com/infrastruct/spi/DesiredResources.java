package com.infrastruct.spi;

import java.util.List;

/**
 * 사용자가 원하는 최종 상태 전체. DesiredStateCreator 가 만들고, Validator 의 검증 대상이 된다.
 *
 * <p>record 인 이유는 {@link ScannedResources} 참조.
 *
 * @param resources 원하는 자원 상태 목록
 */
public record DesiredResources(List<DesiredResourceState> resources) {

    /** 넘어온 목록을 불변으로 복사한다. 근거는 {@link ScannedResources#ScannedResources(List)}. */
    public DesiredResources {
        resources = resources == null ? List.of() : List.copyOf(resources);
    }
}
