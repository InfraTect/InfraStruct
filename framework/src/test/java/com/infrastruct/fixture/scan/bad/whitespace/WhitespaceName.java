package com.infrastruct.fixture.scan.bad.whitespace;

import com.infrastruct.api.Resource;
import com.infrastruct.fixture.scan.ScanVpc;

/** 깨진 자원: logicalId 가 공백뿐이다. */
@Resource(name = "   ")
public class WhitespaceName extends ScanVpc {}
