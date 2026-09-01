package com.infrastruct.fixture.scan.bad.dup;

import com.infrastruct.api.Resource;
import com.infrastruct.fixture.scan.ScanVpc;

/** 깨진 자원: {@link TwinOne} 과 logicalId 가 겹친다. */
@Resource(name = "twin")
public class TwinTwo extends ScanVpc {}
