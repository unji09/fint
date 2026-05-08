package com.s14p31a301.fint.feature.recording.domain.model

import java.io.File

data class RecordingFile(
    val file: File,
    val durationMs: Long,
    val activityId: Long?,
)

