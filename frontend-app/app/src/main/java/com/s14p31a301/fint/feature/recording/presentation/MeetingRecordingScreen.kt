package com.s14p31a301.fint.feature.recording.presentation

import androidx.compose.runtime.Composable

/**
 * 미팅 녹음 메인 화면. RECORD_AUDIO 권한 처리 + 녹음 컨트롤.
 */
@Composable
fun MeetingRecordingScreen(
    activityId: Long?,
    onFinished: (sttJobId: String) -> Unit,
    onCancel: () -> Unit,
) {
    // TODO
}

