package com.infrastruct.fixture.scan.bad.notprovider;

import com.infrastruct.api.Resource;

/** 깨진 자원: {@code ProviderResource} 를 상속하지 않아 kind 를 읽을 데가 없다. */
@Resource(name = "notProvider")
public class NotAProviderResource {}
