package com.infrastruct.fixture.provider;

import com.infrastruct.spi.Validator;

/** 픽스처: 무인자 생성자가 {@code private} 인 검증기. 프레임워크는 {@code setAccessible} 로 뚫지 않는다. */
public class PrivateCtorValidator extends Validator {

    private PrivateCtorValidator() {}
}
