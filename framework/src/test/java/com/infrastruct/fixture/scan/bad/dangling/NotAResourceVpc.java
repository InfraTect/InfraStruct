package com.infrastruct.fixture.scan.bad.dangling;

import com.infrastruct.fixture.scan.ScanVpc;

/** {@code @Resource} 가 없는 클래스. {@code DanglingReference} 가 이것을 참조해 거부를 유발한다. */
public abstract class NotAResourceVpc extends ScanVpc {}
