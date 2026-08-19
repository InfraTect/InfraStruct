package com.infrastruct.internal;

/**
 * 자원 선언이 잘못되어 스캔을 진행할 수 없을 때 던진다.
 *
 * <p>{@link RuntimeException} 인 이유는 이것이 사용자의 <b>선언 실수</b>라서다. 복구 가능한 조건이 아니라 고쳐야 할 코드이므로, checked 로
 * 강제해도 호출부가 할 수 있는 일이 없다.
 *
 * <p>메시지에는 문제가 된 클래스의 FQCN 을 반드시 담는다. 사용자가 자기 코드 어디를 고쳐야 하는지 바로 알아야 한다.
 */
public class ResourceScanException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 사유만 담아 만든다.
     *
     * @param message 문제가 된 클래스의 FQCN 을 포함한 설명
     */
    public ResourceScanException(String message) {
        super(message);
    }

    /**
     * 사유와 원인 예외를 함께 담아 만든다.
     *
     * <p>reflection 실패처럼 아래에서 올라온 예외가 있으면 삼키지 않고 {@code cause} 로 붙인다. 원인을 잃으면 사용자가 진짜 이유를 못 본다.
     *
     * @param message 문제가 된 클래스의 FQCN 을 포함한 설명
     * @param cause 원인 예외
     */
    public ResourceScanException(String message, Throwable cause) {
        super(message, cause);
    }
}
