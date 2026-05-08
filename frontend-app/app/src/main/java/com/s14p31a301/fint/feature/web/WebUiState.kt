package com.s14p31a301.fint.feature.web

data class WebUiState(
    val currentUrl: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

