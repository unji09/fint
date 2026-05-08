package com.s14p31a301.fint.core.media.recorder

import java.io.File

/**
 * 미팅 녹음용 오디오 레코더.
 * MediaRecorder 또는 AudioRecord 기반.
 */
interface AudioRecorder {
    fun start(outputFile: File)
    fun pause()
    fun resume()
    fun stop(): File
    fun isRecording(): Boolean
}

