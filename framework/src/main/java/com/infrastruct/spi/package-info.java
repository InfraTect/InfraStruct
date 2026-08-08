/**
 * 프로바이더(AWS/Azure/…)가 구현·상속하는 확장 지점(Service Provider Interface).
 *
 * <p>예: 리소스 루트 계약, Applier/Validator 인터페이스, 프로바이더 등록 지점.
 *
 * <p><b>가장 안정적이어야 하는 곳.</b> 남의 프로바이더가 우리 위에 서므로, 여기를 깨면 세상의 모든 InfraStruct 프로바이더가 동시에 깨진다.
 */
package com.infrastruct.spi;
