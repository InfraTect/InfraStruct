package com.infrastruct.spi;

import java.lang.annotation.Annotation;

/**
 * 매크로 어노테이션 하나의 효과를 자원 상태에 반영하는 핸들러 계약.
 *
 * <p>확장 작성자가 매크로 어노테이션(예: {@code @AllowSSH})마다 이 인터페이스를 구현한다. 스캔 단계에서 수집된 어노테이션을
 * DesiredStateCreator 가 해당 핸들러의 {@link #handle} 로 전달해 config 에 반영한다.
 *
 * @param <T> 이 핸들러가 처리하는 어노테이션 타입
 */
public interface BehaviorHandler<T extends Annotation> {

    /**
가     * 어노테이션의 효과를 반영한 <b>새 상태</b>를 만들어 돌려준다.
     *
     * <p><b>왜 {@code void} 가 아닌가</b>: {@link ScannedResourceState} 는 불변이라 넘어온 {@code state} 를 고칠 수
     * 없다. 고치는 대신 고쳐진 사본을 돌려주고, 엔진이 그 반환값을 다음 핸들러의 입력으로 이어 붙인다. 그래서 구현은 순수 함수처럼 쓰면 된다 — 넘어온 것과 돌려준 것
     * 말고는 아무것도 바꾸지 않는다.
     *
     * <p>어노테이션 <b>인스턴스</b>가 넘어오므로 인자 값을 읽을 수 있다({@code annotation.port()}).
     *
     * @param annotation 처리할 어노테이션 인스턴스
     * @param state 반영 대상 자원 상태 (고치지 말 것 — 불변이다)
     * @return 효과가 반영된 새 상태. 바꿀 것이 없으면 {@code state} 를 그대로 돌려준다. {@code null} 을 돌려주는 것은 계약 위반이고,
     *     {@code kind} 와 {@code logicalId} 를 바꿔 돌려주는 것도 마찬가지다 ({@code logicalId} 는 뒤 단계에서 상태를 짝지을 때
     *     쓰는 키라 여기서 바뀌면 안 된다). 엔진이 검사해 예외를 던진다.
     */
    ScannedResourceState handle(T annotation, ScannedResourceState state);
}
