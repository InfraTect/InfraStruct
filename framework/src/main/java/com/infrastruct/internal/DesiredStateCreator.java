package com.infrastruct.internal;

import com.infrastruct.spi.BehaviorHandler;
import com.infrastruct.spi.DesiredResourceState;
import com.infrastruct.spi.DesiredResources;
import com.infrastruct.spi.Kind;
import com.infrastruct.spi.ScannedResourceState;
import com.infrastruct.spi.ScannedResources;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 스캔 결과({@link ScannedResources})를 사용자가 원하는 최종 상태({@link DesiredResources})로 변환하는 내부 모듈.
 *
 * <p>파이프라인에서의 자리: scan 직후 {@link #create(ScannedResources)} 로 원하는 상태를 만들고, 그 결과가 {@code
 * Validator.validate(DesiredResources)} 의 입력이 된다.
 *
 * <p>한 줄 정의는 <b>"아직 안 풀린 지시서(매크로 어노테이션)를 전부 풀어, 더 이상 풀 것이 없는 상태로 바꾸는 단계"</b> 다. {@link
 * ScannedResourceState} 와 {@link DesiredResourceState} 를 타입으로 갈라 둔 이유가 그대로 이 모듈의 계약이다 — 이 단계를 지나면
 * {@code capturedAnnotations} 가 사라져 있어야 한다.
 *
 * <p><b>핸들러 레지스트리가 없는 이유</b>: 어떤 어노테이션을 어느 핸들러로 보낼지는 스캐너가 {@code @Behavior} 를 읽는 시점에 이미 정해져 {@link
 * CapturedAnnotation} 에 함께 담긴다. 그래서 이 모듈은 조회하지 않고 <b>풀기만</b> 한다.
 *
 * <p>상태를 들지 않는다. 핸들러 인스턴스 캐시조차 {@link #create(ScannedResources)} 호출 지역에 둬서 스레드 안전성 논의를 아예 없앴다.
 */
public final class DesiredStateCreator {

    /**
     * 스캔 결과를 원하는 최종 상태로 변환한다.
     *
     * <p>자원마다 {@code capturedAnnotations} 를 <b>스캔 순서 그대로</b> 해당 {@link BehaviorHandler#handle} 에
     * 먹이고, 반환값을 다음 핸들러의 입력으로 이어 붙인다(fold). 같은 {@code config} 키를 두 어노테이션이 건드리면 <b>나중 것이 이긴다.</b> 순회
     * 대상은 언제나 원본 목록이라, 핸들러가 어노테이션을 더 붙여 돌려줘도 이번 실행에서는 처리하지 않는다(종료 보장).
     *
     * <p>자원 개수는 변하지 않는다 — 이 단계는 자원을 더하거나 빼지 않고 순서를 지킨 채 1:1 로 옮긴다. {@code requiredFields} 가 실제로
     * 채워졌는지는 <b>검증하지 않는다</b>. 그것은 위반을 모아 보고해야 하는 {@code Validator} 의 일이고, 이 모듈은 변환만 한다.
     *
     * <p><b>config 숫자 규칙(구현 시 반드시 지킬 것): 정수는 {@code Long}, 소수는 {@code Double} 로 넣는다.</b> {@code int
     * port = 22} 를 그대로 오토박싱하면 {@code Integer 22} 가 되는데, {@code CurrentStateStore} 가 복원한 값은 {@code
     * Long 22} 라 {@code Comparator} 의 {@code Objects.equals} 가 둘을 다르다고 본다 → 아무것도 안 바꿔도 매번 UPDATE 가
     * 뜬다. 저장소 쪽 근거는 {@code CurrentStateStore.normalizeConfig} 참조. 이 약속을 구조로 대체하는 방안(정규화를 {@code
     * ResourceState} 생성자로 올리기)은 {@code docs/plan.md} §9 에 열린 항목으로 있다.
     *
     * @param scanned 스캐너가 만든 스캔 결과
     * @return 어노테이션이 모두 풀린 원하는 상태
     * @throws NullPointerException {@code scanned} 가 {@code null} 인 경우
     * @throws DesiredStateException 핸들러를 만들 수 없거나(public 무인자 생성자 없음), {@code @Behavior} 연결이 어노테이션
     *     타입과 어긋나거나, 핸들러가 계약을 어겼을 때({@code null} 반환, {@code kind}/{@code logicalId} 변경) 또는 핸들러가 예외를
     *     던졌을 때. 첫 실패에서 멈추며, 메시지에 자원의 logicalId·어노테이션 타입·핸들러 FQCN 을 담는다
     */
    public DesiredResources create(ScannedResources scanned) {
        Objects.requireNonNull(scanned, "scanned");
        // 캐시를 필드가 아니라 호출 지역에 두는 이유: 이 모듈을 상태 없는 모듈로 유지해 스레드 안전성
        // 논의를 아예 없앤다. 파이프라인에서 create() 는 실행당 한 번이라 재사용으로 얻을 것도 없다.
        Map<Class<? extends BehaviorHandler<?>>, BehaviorHandler<?>> handlers = new HashMap<>();
        List<DesiredResourceState> desired = new ArrayList<>();
        for (ScannedResourceState resource : scanned.resources()) {
            ScannedResourceState acc = resource;
            // 순회 대상은 누적 상태가 아니라 원본 목록이다 — 핸들러가 어노테이션을 더 붙여 돌려줘도
            // 처리하지 않는다. 그러지 않으면 핸들러가 자기 자신을 다시 부르게 만들 수 있다.
            for (CapturedAnnotation captured : resource.capturedAnnotations()) {
                acc = apply(resource.logicalId(), captured, acc, handlers);
            }
            desired.add(toDesired(acc));
        }
        return new DesiredResources(desired);
    }

    // 어노테이션 하나를 핸들러에 먹여 반영된 상태를 얻는다.
    private static ScannedResourceState apply(
            String logicalId,
            CapturedAnnotation captured,
            ScannedResourceState state,
            Map<Class<? extends BehaviorHandler<?>>, BehaviorHandler<?>> handlers) {
        // 핸들러는 상태 없는 함수로 본다 → Class 당 인스턴스 하나를 자원들이 공유한다.
        BehaviorHandler<?> handler =
                handlers.computeIfAbsent(
                        captured.handlerClass(), key -> instantiate(key, logicalId, captured));
        checkAnnotationType(logicalId, captured); // ㅅㅂ 이거 뭔 코드냐
        ScannedResourceState returned = invoke(handler, logicalId, captured, state);
        checkResult(logicalId, captured, state, returned);
        return returned;
    }

    // 핸들러가 계약을 지켰는지 본다. 잠그는 것은 식별자(kind, logicalId)뿐이고, dependencies 나
    // requiredFields 를 더하는 것은 매크로의 정상적인 일이라 허용한다.
    private static void checkResult(
            String logicalId,
            CapturedAnnotation captured,
            ScannedResourceState state,
            ScannedResourceState returned) {
        if (returned == null) {
            throw new DesiredStateException(
                    context(logicalId, captured)
                            + ": 핸들러가 null 을 돌려줬다. 바꿀 것이 없으면 넘어온 상태를 그대로 돌려줘야 한다.");
        }
        if (!logicalId.equals(returned.logicalId())) {
            throw new DesiredStateException(
                    context(logicalId, captured)
                            + ": 핸들러가 logicalId 를 '"
                            + returned.logicalId()
                            + "' 로 바꿨다. logicalId 는 뒤 단계가 상태를 짝지을 때 쓰는 키라 바꿀 수 없다.");
        }
        if (!Objects.equals(state.kind(), returned.kind())) {
            throw new DesiredStateException(
                    context(logicalId, captured)
                            + ": 핸들러가 kind 를 "
                            + describe(state.kind())
                            + " 에서 "
                            + describe(returned.kind())
                            + " 로 바꿨다. 자원의 종류는 바꿀 수 없다.");
        }
    }

    // 핸들러가 던진 예외는 "무엇이 안 됐다"만 안다. 어느 자원의 어느 어노테이션인지 아는 곳은 여기뿐이라
    // 여기서 잡아 맥락을 붙여 다시 던진다 (CONVENTIONS §8.4).
    private static ScannedResourceState invoke(
            BehaviorHandler<?> handler,
            String logicalId,
            CapturedAnnotation captured,
            ScannedResourceState state) {
        try {
            return asAnnotationHandler(handler).handle(captured.anno(), state);
        } catch (RuntimeException e) {
            throw new DesiredStateException(
                    context(logicalId, captured) + ": 핸들러가 처리 중 예외를 던졌다.", e);
        }
    }

    // 부르기 전에 확인한다. 안 그러면 ClassCastException 이 핸들러 '안에서' 터져 사용자가 원인을 못 찾는다.
    private static void checkAnnotationType(String logicalId, CapturedAnnotation captured) {
        Class<?> declared = declaredAnnotationType(captured.handlerClass());
        Class<?> actual = captured.anno().annotationType();
        if (declared != null && !declared.equals(actual)) {
            throw new DesiredStateException(
                    context(logicalId, captured)
                            + ": 핸들러가 선언한 어노테이션 타입은 "
                            + declared.getName()
                            + " 인데 "
                            + actual.getName()
                            + " 이 연결됐다. @Behavior 연결을 확인하라.");
        }
    }

    // 핸들러가 직접 구현한 BehaviorHandler 의 타입 인자. 확정할 수 없으면 null 을 돌려주고,
    // 그때는 검사를 건너뛴다 (raw 구현, 타입 변수를 그대로 넘기는 구현).
    private static Class<?> declaredAnnotationType(Class<?> handlerClass) {
        for (Type type : handlerClass.getGenericInterfaces()) {
            if (type instanceof ParameterizedType parameterized
                    && BehaviorHandler.class.equals(parameterized.getRawType())) {
                Type arg = parameterized.getActualTypeArguments()[0];
                if (arg instanceof Class<?> annotationType) {
                    return annotationType;
                }
            }
        }
        return null;
    }

    // 핸들러 계약은 public 무인자 생성자다. getConstructor() 는 공개 생성자만 찾으므로
    // 비공개 생성자·추상 클래스·생성자가 던진 경우가 모두 여기 한 자리에 걸린다.
    private static BehaviorHandler<?> instantiate(
            Class<? extends BehaviorHandler<?>> handlerClass,
            String logicalId,
            CapturedAnnotation captured) {
        try {
            return handlerClass.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new DesiredStateException(
                    context(logicalId, captured) + ": 핸들러 인스턴스를 만들 수 없다. public 무인자 생성자가 필요하다.", e);
        }
    }

    // Kind 는 프로바이더가 구현하는 인터페이스라 toString 을 신뢰할 수 없다. value() 를 쓴다.
    private static String describe(Kind kind) {
        return kind == null ? "null" : kind.value();
    }

    // 진단 메시지의 공통 접두. 사용자가 고쳐야 할 대상 셋(자원·어노테이션·핸들러)을 모두 담는다.
    // logicalId 는 항상 원본 자원의 것을 쓴다 — 핸들러가 바꿔 돌려준 값을 쓰면 진단이 거짓말을 한다.
    private static String context(String logicalId, CapturedAnnotation captured) {
        return "자원 '"
                + logicalId
                + "' 의 어노테이션 @"
                + captured.anno().annotationType().getName()
                + " (핸들러 "
                + captured.handlerClass().getName()
                + ")";
    }

    // CapturedAnnotation 이 어노테이션과 핸들러 클래스를 따로 들고 있어 "이 핸들러가 이 어노테이션을
    // 받는다"를 컴파일러가 알 수 없다. 비검사 캐스트는 이 한 곳에만 가둔다.
    @SuppressWarnings("unchecked")
    private static BehaviorHandler<Annotation> asAnnotationHandler(BehaviorHandler<?> handler) {
        return (BehaviorHandler<Annotation>) handler;
    }

    // 어노테이션을 다 푼 상태에서 식별자와 값만 옮긴다. capturedAnnotations 는 버린다 —
    // 더 이상 풀 것이 없다는 것이 DesiredResourceState 라는 타입의 의미다.
    private static DesiredResourceState toDesired(ScannedResourceState state) {
        return new DesiredResourceState(
                state.kind(),
                state.logicalId(),
                state.config(),
                state.dependencies(),
                state.requiredFields());
    }
}
