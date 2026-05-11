package com.ssafy.fint.domain.mood;

public enum MoodType {
    RAINBOW(80, 100),
    SUNNY(60, 79),
    CLOUDY(40, 59),
    RAINY(20, 39),
    THUNDER(0, 19);

    private final int min;
    private final int max;

    MoodType(int min, int max) {
        this.min = min;
        this.max = max;
    }

    public static MoodType from(int score) {
        for (MoodType type : values()) {
            if (score >= type.min && score <= type.max) {
                return type;
            }
        }
        return THUNDER; // 0 미만 방어
    }
}
