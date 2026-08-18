package com.infrastruct.fixture.scan.bad.blank;

import com.infrastruct.api.Resource;
import com.infrastruct.fixture.scan.ScanVpc;

/** 깨진 자원: logicalId 가 비었다. state 파일의 키라 비면 안 된다. */
@Resource(name = "")
public class BlankName extends ScanVpc {}
