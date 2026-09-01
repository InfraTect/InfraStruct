package com.infrastruct.fixture.provider;

import com.infrastruct.spi.Validator;

/** 픽스처: 생성자가 예외를 던지는 검증기. reflection 래퍼를 벗겨 진짜 원인을 붙이는지 본다. */
public final class ThrowingValidator extends Validator {

    /** 언제나 터진다. */
    public ThrowingValidator() {
        throw new IllegalStateException("boom");
    }
}
