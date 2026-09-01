package com.infrastruct.fixture.scan.good;

import com.infrastruct.api.Resource;
import com.infrastruct.fixture.scan.ScanVpc;

/** 정상 자원. 사용자가 initializer block 으로 값을 채우는 형태를 따른다 ({@code plan.md} §5). */
@Resource(name = "alphaVpc")
public class GoodVpc extends ScanVpc {

    {
        cidrBlock = "10.0.0.0/16";
    }
}
