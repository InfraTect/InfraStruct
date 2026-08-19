package com.infrastruct.internal;

import com.infrastruct.spi.DesiredResources;
import com.infrastruct.spi.ScannedResources;
import java.util.List;

/**
 * 스캔 결과({@link ScannedResources})를 사용자가 원하는 최종 상태({@link DesiredResources})로 변환하는 내부 모듈.
 *
 * <p>파이프라인에서의 자리: scan 직후 {@link #create(ScannedResources)} 로 원하는 상태를 만들고, 그 결과가 {@code
 * Validator.validate(DesiredResources)} 의 입력이 된다.
 *
 * <p><b>현재 상태: 뼈대(스텁)다.</b> 공개 시그니처만 확정하고 본문은 비워, 다른 사람이 이 타입을 import 해 호출부를 먼저 엮을 수 있게 한다. 실제 변환
 * 로직은 다음 feature 에서 채운다.
 */
public final class DesiredStateCreator {

    /**
     * 스캔 결과를 원하는 최종 상태로 변환한다.
     *
     * <p>원래 의도: {@code scanned} 의 각 자원에서 {@code capturedAnnotations} 를 해당 {@code
     * BehaviorHandler.handle} 로 소비해 {@code config} 에 반영하고, 그 결과를 {@code DesiredResourceState} 로 바꿔
     * {@link DesiredResources} 로 묶는다. 아직 {@code handle} 시그니처 확정과 핸들러 레지스트리가 서지 않아(→ plan §4) 본문은 비워
     * 두고, 빈 상태를 돌려준다.
     *
     * <p><b>config 숫자 규칙(구현 시 반드시 지킬 것): 정수는 {@code Long}, 소수는 {@code Double} 로 넣는다.</b> {@code int
     * port = 22} 를 그대로 오토박싱하면 {@code Integer 22} 가 되는데, {@code CurrentStateStore} 가 복원한 값은 {@code
     * Long 22} 라 {@code Comparator} 의 {@code Objects.equals} 가 둘을 다르다고 본다 → 아무것도 안 바꿔도 매번 UPDATE 가
     * 뜬다. 저장소 쪽 근거는 {@code CurrentStateStore.normalizeConfig} 참조. 이 약속을 구조로 대체하는 방안(정규화를 {@code
     * ResourceState} 생성자로 올리기)은 {@code docs/plan.md} §9 에 열린 항목으로 있다.
     *
     * @param scanned 스캐너가 만든 스캔 결과
     * @return 변환된 원하는 상태 (스텁: 원소 없는 {@link DesiredResources})
     */
    public DesiredResources create(ScannedResources scanned) {
        // TODO: scanned 의 각 자원 capturedAnnotations 를 BehaviorHandler.handle 로 소비 → config 반영
        //       → DesiredResourceState 변환 → DesiredResources.
        //       config 에 넣을 때 정수는 Long, 소수는 Double 로 통일한다 (위 Javadoc 참조).
        return new DesiredResources(List.of());
    }
}
