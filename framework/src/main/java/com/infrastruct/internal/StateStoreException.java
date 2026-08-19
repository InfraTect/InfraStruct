package com.infrastruct.internal;

/**
 * 상태 파일을 읽거나 쓰지 못했을 때 던진다.
 *
 * <p>검사 예외가 아니라 {@link RuntimeException} 인 이유: 상태 파일이 깨졌다는 것은 호출부가 그 자리에서 복구할 수 있는 사건이 아니다. 파이프라인
 * 중간의 모든 단계가 {@code throws} 를 달고 다니는 대신, 경계({@code InfraStruct.run()})가 한 번 잡아 사용자에게 보여준다.
 *
 * <p>메시지에는 <b>항상 상태 파일 경로가 들어간다</b> — 경계가 {@link #getMessage()} 를 그대로 사용자에게 보여줄 수 있어야 하기 때문이다.
 */
public class StateStoreException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 원인 예외 없이 만든다.
     *
     * @param message 상태 파일 경로를 포함한 설명
     */
    public StateStoreException(String message) {
        super(message);
    }

    /**
     * 원인 예외를 달아 만든다.
     *
     * @param message 상태 파일 경로를 포함한 설명
     * @param cause 원래 터진 예외 (보통 {@link java.io.IOException} 이나 JSON 파싱 예외)
     */
    public StateStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
