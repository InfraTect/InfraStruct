package com.infrastruct.internal;

/**
 * 스캔 결과를 원하는 상태로 옮기는 중 진행할 수 없는 문제를 만났을 때 던진다.
 *
 * <p>{@link RuntimeException} 인 이유는 이것이 <b>프로바이더나 사용자의 선언 실수</b>라서다. 잘못 연결된 핸들러나 계약을 어긴 핸들러는 고쳐야 할
 * 코드이지 호출부가 잡아서 복구할 조건이 아니다.
 *
 * <p>메시지에는 문제가 된 자원의 logicalId, 어노테이션 타입, 핸들러 FQCN 을 담는다. 이 셋이 없으면 "어느 자원의 어느 어노테이션이 문제인가"를 알 수 없다.
 */
public class DesiredStateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 사유만 담아 만든다.
     *
     * @param message 자원 logicalId 와 핸들러 FQCN 을 포함한 설명
     */
    public DesiredStateException(String message) {
        super(message);
    }

    /**
     * 사유와 원인 예외를 함께 담아 만든다.
     *
     * <p>핸들러 인스턴스화 실패나 핸들러가 던진 예외처럼 아래에서 올라온 예외는 삼키지 않고 {@code cause} 로 붙인다.
     *
     * @param message 자원 logicalId 와 핸들러 FQCN 을 포함한 설명
     * @param cause 원인 예외
     */
    public DesiredStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
