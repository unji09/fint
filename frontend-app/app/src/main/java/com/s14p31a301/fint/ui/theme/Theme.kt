package com.s14p31a301.fint.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * F!NT 테마. 시안과 통일된 라이트 전용 팔레트.
 * (시안의 디자인이 라이트 모드 기준이므로 dark/dynamic 비활성)
 */
private val FintLightColors = lightColorScheme(
    primary = BrandCyan,
    onPrimary = SurfaceCard,
    primaryContainer = CyanLight,
    onPrimaryContainer = DeepCyan,
    secondary = DeepCyan,
    onSecondary = SurfaceCard,
    background = PageBg,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = Border,
    onSurfaceVariant = TextSecondary,
    outline = Border,
    error = Danger,
    onError = SurfaceCard,
)

@Composable
fun FintTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FintLightColors,
        typography = Typography,
        content = content
    )
}
