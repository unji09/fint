package com.s14p31a301.fint.app

import androidx.compose.runtime.Composable
import com.s14p31a301.fint.app.navigation.FintNavHost
import com.s14p31a301.fint.ui.theme.FintTheme

/**
 * 최상위 Composable. Theme + NavHost 호스팅.
 */
@Composable
fun FintApp() {
    FintTheme {
        FintNavHost()
    }
}
