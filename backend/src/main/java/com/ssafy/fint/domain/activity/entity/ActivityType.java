package com.ssafy.fint.domain.activity.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
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

    /**
     * JSON 역직렬화 시 enum 이름("MEETING") 또는 displayName("미팅") 모두 수용한다.
     */
    @JsonCreator
    public static ActivityType from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (ActivityType type : values()) {
            if (type.name().equalsIgnoreCase(value) || type.displayName.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid ActivityType: " + value);
    }
}
