package com.infrastruct.spi;

/** 자원(또는 자원의 필드/의존성)이 어떻게 바뀌는지를 나타낸다. */
public enum ChangeType {
    CREATE,
    UPDATE,
    DELETE
}
