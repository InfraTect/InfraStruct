package com.infrastruct.spi;

import java.util.List;

/**
 * ResourceScanner 의 스캔 결과 전체. {@code ResourceScanner.scan()} 의 반환 타입이다.
 *
 * <p>record 인 이유: 파이프라인은 컨테이너를 <b>바꿔 끼우지 덧붙이지 않는다</b>. 각 단계가 새 컨테이너를 만들어 다음 단계로 넘기므로 컨테이너 자체는 불변이어도
 * 된다. (안에 든 {@link ScannedResourceState} 는 가변이다 — 근거는 {@link ResourceState#getConfig()}.)
 *
 * @param scannedResources 스캔된 자원 상태 목록
 */
public record ScannedResources(List<ScannedResourceState> scannedResources) {

    /**
     * 넘어온 목록을 불변으로 복사한다.
     *
     * <p>{@code null} 을 빈 목록으로 바꾸는 이유: 상태 파일에 키가 없거나 비어 있을 때 Gson 이 {@code null} 을 넘기는데, 그대로 두면 한참
     * 뒤 엉뚱한 곳에서 NPE 가 난다.
     */
    public ScannedResources {
        scannedResources = scannedResources == null ? List.of() : List.copyOf(scannedResources);
    }
}
