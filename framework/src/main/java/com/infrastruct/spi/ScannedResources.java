package com.infrastruct.spi;

import java.util.List;

/**
 * ResourceScanner 의 스캔 결과 전체. {@code ResourceScanner.scan()} 의 반환 타입이다.
 *
 * <p>record 인 이유: 파이프라인은 컨테이너를 <b>바꿔 끼우지 덧붙이지 않는다</b>. 각 단계가 새 컨테이너를 만들어 다음 단계로 넘기므로 컨테이너 자체가 불변이어도
 * 된다.
 *
 * @param resources 스캔된 자원 상태 목록
 */
public record ScannedResources(List<ScannedResourceState> resources) {

    /**
     * 넘어온 목록을 불변으로 복사한다.
     *
     * @throws NullPointerException {@code resources} 가 {@code null} 이거나 원소에 {@code null} 이 섞인 경우
     */
    public ScannedResources {
        resources = List.copyOf(resources);
    }
}
