package com.infrastruct.fixture.scan;

import com.infrastruct.spi.ProviderResource;

/**
 * 스캔 test 전용 자원 루트. 실제 프로바이더의 {@code AwsResource} 자리다.
 *
 * <p>{@code owner} 는 <b>조부모 필드까지 읽는지</b> 보는 장치다. {@code GoodVpc} 기준으로 이 클래스는 조부모다.
 */
public abstract class ScanResource extends ProviderResource {

    /** 자식이 shadowing 하지 않으면 이 값이 config 에 들어가야 한다. */
    public String owner = "infra-team";

    protected ScanResource() {
        this.provider = ScanProvider.class;
    }
}
