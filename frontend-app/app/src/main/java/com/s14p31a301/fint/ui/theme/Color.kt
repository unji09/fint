package com.s14p31a301.fint.ui.theme

import androidx.compose.ui.graphics.Color

// --- Default M3 baseline (예전 템플릿에서 자동 생성) ---
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// ----------------------------------------------------------------
// F!NT Design System Tokens
// ----------------------------------------------------------------

// 01. 브랜드
val BrandCyan = Color(0xFF06B6D4)   // MAIN — 액티브 / 강조 / 선택 테두리
val DeepCyan = Color(0xFF0E7490)    // SUB — 헤더 / 강조 버튼 / 섹션 제목
val PageBg = Color(0xFFF5F7FA)      // 본 화면 배경
val SurfaceCard = Color(0xFFFFFFFF) // 카드 / 모달
val Border = Color(0xFFE2EAF0)      // 카드/입력 경계 · 분리선 (--color-border)

// 02. 텍스트
val TextPrimary = Color(0xFF1E293B)
val TextSecondary = Color(0xFF475569)
val TextMuted = Color(0xFF64748B)
val Placeholder = Color(0xFF94A3B8)

// 03. 시스템 (위험도)
val Danger = Color(0xFFEF4444)
val Warning = Color(0xFFEA580C)
val Caution = Color(0xFFF59E0B)
val Success = Color(0xFF22C55E)

// 보조 톤 — 색상규칙 표에 단독 항목은 없으나 Brand Cyan 의 옅은 tint.
// 시안의 작은 아이콘 배경/연한 보더 표현을 위한 보조 토큰. (CFFAFE / A5F3FC 는 Tailwind cyan-100/200 톤)
val CyanLight = Color(0xFFCFFAFE)
val CyanBorder = Color(0xFFA5F3FC)

// 명함 미리보기 그라디언트 — UI 문서에는 없는 "콘텐츠 자산(시안 디자인 그대로)"용.
// 토큰이 아니므로 일반 화면에서는 사용하지 말 것.
val CardGradStart = Color(0xFF0A5258)
val CardGradMid = Color(0xFF0D6B70)
val CardGradEnd = Color(0xFF1A8A8F)
