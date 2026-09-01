package com.infrastruct.fixture.scan.bad.dup;

import com.infrastruct.api.Resource;
import com.infrastruct.fixture.scan.ScanVpc;

/** 깨진 자원: {@link TwinTwo} 와 logicalId 가 겹친다. */
@Resource(name = "twin")
public class TwinOne extends ScanVpc {}
