package com.infrastruct.api;

/**
 * 프레임워크의 진입 클래스. 사용자의 메인에서 이 클래스를 통해 실행을 시작한다.
 *
 * <p><b>현재 상태: 뼈대(스텁)다.</b> 다이어그램상 이 클래스는 내부에 모듈 7개 (ResourceScanner, DesiredStateCreator,
 * Validator, CurrentStateStore, Comparator, PlanCreator, Applier)를 필드로 들고, 이들을 순서대로 호출해
 * 스캔→비교→플랜→적용을 수행한다. 하지만 그 모듈 타입들과 이들을 찾아 주입해 줄 {@code ModuleRegistry} 가 아직 다른 브랜치에서 구현 중이라, 지금은
 * 참조할 수 없다.
 *
 * <p>그래서 이번 feature 에서는 <b>공개 시그니처만</b> 확정하고 본문은 비워 둔다. 채워질 자리는 각 메서드의 주석으로 표시해 두었다.
 */
public class InfraStruct {

    // 원래 여기에 모듈 필드 7개가 온다:
    //   resourceScanner, desiredStateCreator, validator, currentStateStore,
    //   comparator, planCreator, applier
    // 타입이 아직 없어(다른 브랜치) 선언하면 컴파일이 깨지므로 이번엔 두지 않는다.

    /**
     * 사용자의 메인 클래스로부터 프레임워크 실행을 시작하는 정적 진입점.
     *
     * <p>원래 의도: {@code mainClass} 에 붙은 {@link InfraStructApplication} 을 리플렉션으로 읽어 {@code
     * provider()} 문자열을 얻고 → {@code new InfraStruct(provider)} 로 컨텍스트를 준비한 뒤 → {@link #run()} 을
     * 호출한다. 아직 그 연결을 채우지 않아 본문은 비어 있다.
     *
     * @param mainClass {@link InfraStructApplication} 이 붙은 사용자 메인 클래스
     */
    public static void run(Class<?> mainClass) {
        // TODO: mainClass 의 @InfraStructApplication 읽기 → provider 추출
        //       → new InfraStruct(provider).run().
    }

    /**
     * provider 식별자로 실행 컨텍스트를 준비한다.
     *
     * <p>원래 의도: {@code ModuleRegistry} 가 이 provider 문자열(예: {@code "aws"})로 해당 프로바이더의
     * Validator/Applier 등 모듈을 찾아 위 필드들에 주입한다. ModuleRegistry 가 아직 없어 지금은 본문을 비워 둔다.
     *
     * @param provider 사용할 프로바이더 식별자
     */
    public InfraStruct(String provider) {
        // TODO: ModuleRegistry 로 provider 의 모듈을 찾아 필드에 주입.
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
}
