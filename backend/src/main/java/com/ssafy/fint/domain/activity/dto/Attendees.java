package com.ssafy.fint.domain.activity.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record Attendees(
        List<String> internal,
        List<String> external
) {

    private static final String EXTERNAL = "external";
    private static final String KEY_TYPE = "type";
    private static final String KEY_NAME = "name";

    public static Attendees from(List<Map<String, Object>> raw) {
        List<String> internal = new ArrayList<>();
        List<String> external = new ArrayList<>();
        if (raw == null) {
            return new Attendees(internal, external);
        }
        for (Map<String, Object> entry : raw) {
            if (entry == null) {
                continue;
            }
            Object name = entry.get(KEY_NAME);
            if (name == null) {
                continue;
            }
            Object type = entry.get(KEY_TYPE);
            if (type != null && EXTERNAL.equals(type.toString())) {
                external.add(name.toString());
            } else {
                internal.add(name.toString());
            }
        }
        return new Attendees(internal, external);
    }
}
