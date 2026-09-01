package com.infrastruct.fixture.scan.bad.inner;

import com.infrastruct.api.Resource;
import com.infrastruct.fixture.scan.ScanVpc;

/** 깨진 자원: logicalId 중간에 공백이 섞였다. 렌더링과 state 파일 키가 깨진다. */
@Resource(name = "my ec2")
public class InnerSpaceName extends ScanVpc {}
