package com.infrastruct.internal;

/**
 * 프로바이더 모듈을 찾거나 만들 수 없어 파이프라인을 시작할 수 없을 때 던진다.
 *
 * <p>{@link RuntimeException} 인 이유는 이것이 <b>설정 실수</b>라서다. 사용자가 선언한 provider 이름이 틀렸거나, 프로바이더 쪽 등록이
 * 잘못된 것이지 실행 중 복구할 수 있는 조건이 아니다. checked 로 강제해도 호출부가 할 수 있는 일이 없다.
 *
 * <p>메시지에는 <b>사용자가 고쳐야 할 대상</b>을 반드시 담는다 — 찾지 못한 provider 이름, 문제가 된 클래스의 FQCN 처럼.
 */
public class ModuleRegistryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 사유만 담아 만든다.
     *
     * @param message 고쳐야 할 대상(provider 이름·클래스 FQCN)을 포함한 설명
     */
    public ModuleRegistryException(String message) {
        super(message);
    }

    /**
     * 사유와 원인 예외를 함께 담아 만든다.
     *
     * <p>구현체 생성자가 던진 예외처럼 아래에서 올라온 원인이 있으면 삼키지 않고 {@code cause} 로 붙인다. 원인을 잃으면 사용자가 진짜 이유를 못 본다.
     *
     * @param message 고쳐야 할 대상(provider 이름·클래스 FQCN)을 포함한 설명
     * @param cause 원인 예외
     */
    public ModuleRegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}
