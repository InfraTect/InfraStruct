package com.infrastruct.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

/**
 * {@link InfraStruct} 진입 클래스의 뼈대 계약을 검증한다.
 *
 * <p>이 feature 에서 InfraStruct 는 본문 없는 스텁이다 — 의존 모듈(ModuleRegistry 등)이 아직 없기 때문. 따라서 "호출해도 예외 없이
 * 반환한다" 수준만 확인한다.
 */
class InfraStructTest {

    /** 픽스처: 사용자의 메인 클래스를 흉내 낸다. */
    @InfraStructApplication(provider = "aws")
    static class SampleApp {}

    @Test
    void constructsWithProvider() {
        // 생성자가 예외를 던지면 여기서 실패한다. 반환값을 실제로 써서 "부수효과 없는 호출" 경고도 피한다.
        assertThat(new InfraStruct("aws")).isNotNull();
    }

    @Test
    void instanceRunDoesNotThrow() {
        InfraStruct app = new InfraStruct("aws");

        assertThatCode(app::run).doesNotThrowAnyException();
    }

    @Test
    void staticRunDoesNotThrow() {
        assertThatCode(() -> InfraStruct.run(SampleApp.class)).doesNotThrowAnyException();
    }
}
