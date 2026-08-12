package com.infrastruct.spi;

/**
 * 마지막으로 apply 된 자원의 실제 상태. CurrentStateStore 가 JSON 으로 저장하고 다시 읽어 들이는 대상이다.
 *
 * <p>{@link DesiredResourceState} 가 "되어야 할 모습"이라면 이쪽은 "지금 실제 모습"이다. Comparator 가 둘을 비교해 변경 목록을 만든다.
 *
 * <p>{@code requiredFields} 는 채우지 않는다 — 이미 적용된 상태에는 "필수 여부"라는 개념이 없다. 근거는 {@link ResourceState} 참조.
 */
public class CurrentResourceState extends ResourceState {

    /**
     * 클라우드가 실제로 발급한 식별자. 예: {@code i-0abc123}.
     *
     * <p>apply 하기 전에는 존재하지 않으므로 {@code null} 일 수 있다.
     */
    private String physicalId;

    /** Gson 역직렬화용 인자 없는 생성자. 근거는 {@link ResourceState#ResourceState()}. */
    public CurrentResourceState() {}

    /**
     * 적용 결과를 담는 생성자.
     *
     * @param kind 자원의 종류
     * @param logicalId 자원의 논리 식별자
     * @param physicalId 클라우드가 발급한 식별자
     */
    public CurrentResourceState(Kind kind, String logicalId, String physicalId) {
        super(kind, logicalId);
        this.physicalId = physicalId;
    }

    /**
     * 클라우드가 발급한 식별자를 반환한다.
     *
     * @return 발급 전이면 {@code null}
     */
    public String getPhysicalId() {
        return physicalId;
    }

    /**
     * 클라우드가 발급한 식별자를 설정한다.
     *
     * <p>이 클래스에서 세터가 있는 유일한 필드다. kind·logicalId 는 스캔 시점에 확정되는 정체성 값이라 바뀌면 Comparator 의 매칭 기준이
     * 무너지지만, physicalId 는 클라우드가 만들어 주므로 apply 이후에야 알 수 있다.
     *
     * @param physicalId 클라우드가 발급한 식별자
     */
    public void setPhysicalId(String physicalId) {
        this.physicalId = physicalId;
    }
}
