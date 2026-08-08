package com.infrastruct;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 빌드 골격(JUnit 5 + AssertJ)이 동작하는지 확인하는 임시 스모크 테스트.
 *
 * <p>실제 프레임워크 코드를 작성하기 시작하면 이 파일은 지워도 된다.
 */
class ScaffoldSmokeTest {

    @Test
    void toolchainIsWired() {
        assertThat(1 + 1).isEqualTo(2);
    }
}
