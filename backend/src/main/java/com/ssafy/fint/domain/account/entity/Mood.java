package com.ssafy.fint.domain.account.entity;

/**
 * 고객사 분위기(날씨) 5단계.
 * 무지개(좋음) → 천둥(나쁨) 순으로 관계 강도가 약해진다.
 * 한국어 노출(무지개/맑음/흐림/비/천둥)은 프론트가 매핑한다.
 */
public enum Mood {
    RAINBOW,
    SUNNY,
    CLOUDY,
    RAINY,
    THUNDER
}
