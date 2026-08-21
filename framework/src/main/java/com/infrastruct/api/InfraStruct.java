package com.infrastruct.api;

import com.infrastruct.internal.ModuleRegistry;
import com.infrastruct.internal.ModuleRegistryException;
import com.infrastruct.spi.Applier;
import com.infrastruct.spi.Validator;

/**
 * 프레임워크의 진입 클래스. 사용자의 메인에서 이 클래스를 통해 실행을 시작한다.
 *
 * <p>다이어그램상 이 클래스는 내부에 모듈 7개 (ResourceScanner, DesiredStateCreator, Validator, CurrentStateStore,
 * Comparator, PlanCreator, Applier)를 필드로 들고, 이들을 순서대로 호출해 스캔→비교→플랜→적용을 수행한다.
 *
 * <p><b>현재 상태:</b> 그중 {@link ModuleRegistry} 가 찾아 주는 두 개({@link Validator}, {@link Applier})가 생성자에서
 * 주입된다. 나머지 다섯 모듈과 {@link #run()} 의 파이프라인 본문은 아직 비어 있다.
 */
public class InfraStruct {

    // 원래 여기에 모듈 필드 7개가 온다:
    //   resourceScanner, desiredStateCreator, validator, currentStateStore,
    //   comparator, planCreator, applier
    // 이번 feature 는 ModuleRegistry 가 찾아 주는 두 개(validator, applier)만 채운다.

    private final Validator validator;

    private final Applier applier;

    /**
     * 사용자의 메인 클래스로부터 프레임워크 실행을 시작하는 정적 진입점.
     *
     * <p>{@code mainClass} 에 붙은 {@link InfraStructApplication} 을 리플렉션으로 읽어 {@code provider()} 문자열을
     * 얻고 → {@code new InfraStruct(provider)} 로 컨텍스트를 준비한 뒤 → {@link #run()} 을 호출한다.
     *
     * @param mainClass {@link InfraStructApplication} 이 붙은 사용자 메인 클래스
     * @throws ModuleRegistryException 어노테이션이 없거나 provider 를 찾지 못한 경우
     */
    public static void run(Class<?> mainClass) {
        InfraStructApplication declaration = mainClass.getAnnotation(InfraStructApplication.class);
        if (declaration == null) {
            throw new ModuleRegistryException(
                    mainClass.getName()
                            + " 에 @InfraStructApplication 이 없습니다."
                            + " 메인 클래스에 @InfraStructApplication(provider = \"...\") 을 붙이세요.");
        }
        new InfraStruct(declaration.provider()).run();
    }

    /**
     * provider 식별자로 실행 컨텍스트를 준비한다.
     *
     * <p>{@link ModuleRegistry} 가 이 provider 문자열(예: {@code "aws"})로 해당 프로바이더의 Validator/Applier 를
     * 찾아 필드에 주입한다. 설정이 잘못됐다면 파이프라인을 돌기 전 <b>여기서</b> 즉시 실패한다.
     *
     * @param provider 사용할 프로바이더 식별자
     * @throws ModuleRegistryException provider 를 찾지 못하거나 등록이 잘못된 경우
     */
    public InfraStruct(String provider) {
        ModuleRegistry registry = new ModuleRegistry(provider);
        this.validator = registry.validator();
        this.applier = registry.applier();
    }

    /**
     * 준비된 모듈들로 실행 파이프라인을 한 번 돈다.
     *
     * <p>원래 의도(다이어그램의 "핵심 모듈 실행 흐름"): scan → desired 생성 → validate → current 상태 load → compare →
     * plan 생성 → apply → 상태 save. 모듈들이 아직 없어 지금은 본문을 비워 둔다.
     */
    public void run() {
        // TODO: scan → desired → validate → load → compare → plan → apply → save 파이프라인.
    }

    /**
     * 주입된 검증기.
     *
     * <p>주입 결과를 테스트가 읽도록 열어 둔 package-private 접근자다.
     *
     * @return 이 실행이 쓸 {@link Validator}
     */
    Validator validator() {
        return validator;
    }

    /**
     * 주입된 적용기.
     *
     * @return 이 실행이 쓸 {@link Applier}
     */
    Applier applier() {
        return applier;
    }
}
