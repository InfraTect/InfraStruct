package com.infrastruct.internal;

import com.infrastruct.spi.CurrentResources;
import java.util.List;

/**
 * 마지막으로 apply 된 실제 상태({@link CurrentResources})를 JSON 파일로 저장하고 다시 복원하는 내부 모듈.
 *
 * <p>파이프라인에서의 자리: compare 직전에 {@link #load()} 로 이전 상태를 읽어 오고, apply 가 끝난 뒤 Applier 가 새로 만들어 준
 * {@link CurrentResources} 를 저장한다.
 *
 * <p><b>현재 상태: 뼈대(스텁)다.</b> 공개 시그니처만 확정하고 본문은 비워, 다른 사람이 이 타입을 import 해 호출부를 먼저 엮을 수 있게 한다. 실제 직렬화
 * 로직은 다음 feature 에서 채운다.
 */
public final class CurrentStateStore {

    /**
     * 저장된 상태 파일을 읽어 {@link CurrentResources} 로 복원한다.
     *
     * <p>원래 의도: 상태 JSON 을 열어 역직렬화한다(Kind {@code TypeAdapter} + 불변 상태 정규화 필요 —
     * resource-state-classes summary §7). 파일이 아직 없으면(최초 실행) 빈 {@link CurrentResources} 를 돌려준다. 지금은
     * 스텁이라 항상 빈 값을 반환한다.
     *
     * @return 복원된 현재 상태 (스텁: 원소 없는 {@link CurrentResources})
     */
    public CurrentResources load() {
        // TODO: 상태 파일 읽기 → Gson 역직렬화(Kind TypeAdapter + null 정규화) → CurrentResources.
        return new CurrentResources(List.of());
    }

    /**
     * 현재 상태를 JSON 파일로 저장한다.
     *
     * <p>원래 의도: {@code resources} 를 직렬화해 상태 파일에 기록한다. 지금은 스텁이라 아무것도 하지 않는다.
     *
     * @param resources 저장할 현재 상태
     */
    public void save(CurrentResources resources) {
        // TODO: CurrentResources 직렬화 → 상태 파일에 기록.
    }
}
