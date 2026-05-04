package com.ssafy.fint.domain.activity.entity;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class StringToActivityTypeConverter implements Converter<String, ActivityType> {

    @Override
    public ActivityType convert(@NonNull String source) {
        if (source.isBlank()) {
            return null;
        }
        for (ActivityType type : ActivityType.values()) {
            if (type.name().equalsIgnoreCase(source) || type.getDisplayName().equals(source)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid ActivityType: " + source);
    }
}
