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

    /**
     * 넘어온 목록을 불변으로 복사한다.
     *
     * @throws NullPointerException {@code resources} 가 {@code null} 이거나 원소에 {@code null} 이 섞인 경우
     */
    public DesiredResources {
        resources = List.copyOf(resources);
    }
}
