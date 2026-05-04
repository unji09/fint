package com.ssafy.fint.domain.activity.entity;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ActivityType {

    MEETING("미팅"),
    CALL("통화"),
    TASK("업무"),
    EMAIL("이메일");

    @JsonValue
    private final String displayName;
}
