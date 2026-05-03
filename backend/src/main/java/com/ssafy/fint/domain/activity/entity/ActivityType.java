package com.ssafy.fint.domain.activity.entity;

/**
 * 활동 유형.
 * DB 에는 문자열(이름) 그대로 저장된다.
 */
public enum ActivityType {
    MEETING,
    CALL,
    TASK,
    EMAIL
}
