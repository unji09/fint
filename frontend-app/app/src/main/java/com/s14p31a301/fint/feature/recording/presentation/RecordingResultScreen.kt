package com.s14p31a301.fint.feature.recording.presentation

import androidx.compose.runtime.Composable

/**
 * 녹음 업로드 후 STT 처리 진행/완료 표시.
 * 완료 시 WebView 의 activity 상세로 reload.
 */
@Composable
fun RecordingResultScreen(
    sttJobId: String,
    onDone: (activityId: Long) -> Unit,
) {
    // TODO
}

